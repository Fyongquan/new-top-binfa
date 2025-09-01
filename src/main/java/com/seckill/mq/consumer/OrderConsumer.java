package com.seckill.mq.consumer;

import com.rabbitmq.client.Channel;
import com.seckill.dto.OrderMessage;
import com.seckill.mq.producer.OrderProducer;
import com.seckill.service.OrderService;
import com.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单消息消费者
 * 
 * @author seckill-test
 */
@Slf4j
@Component
public class OrderConsumer {

  @Resource
  private OrderService orderService;

  @Resource
  private SeckillService seckillService;

  @Resource
  private OrderProducer orderProducer;

  // 幂等性控制：记录已处理的消息ID
  private static final Set<String> processedMessages = ConcurrentHashMap.newKeySet();

  /**
   * 处理订单创建消息
   * 
   * @param orderMessage 订单消息
   * @param channel      消息通道
   * @param deliveryTag  消息标签
   */
  @RabbitListener(queues = "seckill.order.queue")
  public void handleOrderMessage(@Payload OrderMessage orderMessage,
      Channel channel,
      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

    String messageId = orderMessage.getMessageId();
    Long orderId = orderMessage.getOrderId();
    Long userId = orderMessage.getUserId();
    Long voucherId = orderMessage.getVoucherId();

    try {
      log.info("开始处理订单消息 - 消息ID: {}, 订单: {}, 用户: {}, 优惠券: {}",
          messageId, orderId, userId, voucherId);

      // 幂等性检查
      if (processedMessages.contains(messageId)) {
        log.info("消息已处理过，跳过 - 消息ID: {}", messageId);
        channel.basicAck(deliveryTag, false);
        return;
      }

      // 处理订单创建
      boolean success = orderService.createOrder(userId, voucherId, orderId);

      if (success) {
        // 订单创建成功
        processedMessages.add(messageId);
        channel.basicAck(deliveryTag, false);

        log.info("订单消息处理成功 - 消息ID: {}, 订单: {}", messageId, orderId);

      } else {
        // 订单创建失败，进行重试或回滚
        handleOrderFailure(orderMessage, channel, deliveryTag, "订单创建失败");
      }

    } catch (Exception e) {
      log.error("处理订单消息异常 - 消息ID: {}, 订单: {}", messageId, orderId, e);
      handleOrderFailure(orderMessage, channel, deliveryTag, e.getMessage());
    }
  }

  /**
   * 处理死信队列消息
   * 
   * @param orderMessage 订单消息
   * @param channel      消息通道
   * @param deliveryTag  消息标签
   */
  @RabbitListener(queues = "seckill.order.dlx.queue")
  public void handleDeadLetterMessage(@Payload OrderMessage orderMessage,
      Channel channel,
      @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

    log.info("📮 收到死信队列消息 - 消息ID: {}, 订单: {}, 重试次数: {}",
        orderMessage.getMessageId(), orderMessage.getOrderId(), orderMessage.getRetryCount());

    try {
      if (orderMessage.canRetry()) {
        // 可以重试，发送到延迟重试队列
        orderMessage.incrementRetry();
        orderProducer.sendDelayRetryMessage(orderMessage, calculateRetryDelay(orderMessage.getRetryCount()));

        log.info("💫 死信消息转入延迟重试 - 消息ID: {}, 重试次数: {}",
            orderMessage.getMessageId(), orderMessage.getRetryCount());

        channel.basicAck(deliveryTag, false);
      } else {
        // 超过最大重试次数，执行库存回滚
        seckillService.rollbackStock(
            orderMessage.getVoucherId(),
            orderMessage.getUserId(),
            orderMessage.getOrderId());

        notifyAdministrator(orderMessage);
        processedMessages.add(orderMessage.getMessageId());
        channel.basicAck(deliveryTag, false);

        log.error("💀 消息超过最大重试次数，已执行库存回滚 - 消息ID: {}, 订单: {}",
            orderMessage.getMessageId(), orderMessage.getOrderId());
      }
    } catch (Exception e) {
      log.error("💥 处理死信消息异常 - 消息ID: {}", orderMessage.getMessageId(), e);
      try {
        channel.basicNack(deliveryTag, false, false);
      } catch (IOException ioException) {
        log.error("死信消息Nack失败", ioException);
      }
    }
  }

  /**
   * 处理订单失败情况
   * 
   * @param orderMessage 订单消息
   * @param channel      消息通道
   * @param deliveryTag  消息标签
   * @param reason       失败原因
   */
  private void handleOrderFailure(OrderMessage orderMessage, Channel channel,
      long deliveryTag, String reason) {
    try {
      if (orderMessage.canRetry()) {
        // 可以重试，发送延迟重试消息
        int delaySeconds = calculateRetryDelay(orderMessage.getRetryCount());
        orderProducer.sendDelayRetryMessage(orderMessage, delaySeconds);

        log.warn("订单处理失败，将进行重试 - 消息ID: {}, 订单: {}, 重试次数: {}, 延迟: {}秒, 原因: {}",
            orderMessage.getMessageId(), orderMessage.getOrderId(),
            orderMessage.getRetryCount(), delaySeconds, reason);

        channel.basicAck(deliveryTag, false);

      } else {
        // 超过最大重试次数，执行库存回滚
        seckillService.rollbackStock(
            orderMessage.getVoucherId(),
            orderMessage.getUserId(),
            orderMessage.getOrderId());

        processedMessages.add(orderMessage.getMessageId());
        channel.basicAck(deliveryTag, false);

        log.error("订单处理最终失败，已执行库存回滚 - 消息ID: {}, 订单: {}, 原因: {}",
            orderMessage.getMessageId(), orderMessage.getOrderId(), reason);
      }

    } catch (Exception e) {
      log.error("处理订单失败情况时发生异常 - 消息ID: {}, 订单: {}",
          orderMessage.getMessageId(), orderMessage.getOrderId(), e);
      try {
        // 消息处理异常，拒绝消息但不重新入队
        channel.basicNack(deliveryTag, false, false);
      } catch (IOException ioException) {
        log.error("消息Nack失败", ioException);
      }
    }
  }

  /**
   * 计算重试延迟时间
   * 
   * @param retryCount 重试次数
   * @return 延迟秒数
   */
  private int calculateRetryDelay(int retryCount) {
    // 递增延迟：5s -> 10s -> 30s
    switch (retryCount) {
      case 1:
        return 5;
      case 2:
        return 10;
      case 3:
        return 30;
      default:
        return 60;
    }
  }

  /**
   * 通知管理员处理失败消息
   * 可以扩展为发送邮件、短信、钉钉等通知
   * 
   * @param orderMessage 失败的订单消息
   */
  private void notifyAdministrator(OrderMessage orderMessage) {
    try {
      // 这里可以集成邮件、短信、钉钉等通知服务
      log.error("🚨🚨🚨 ADMIN ALERT 🚨🚨🚨");
      log.error("订单处理最终失败，需要人工介入！");
      log.error("消息ID: {}", orderMessage.getMessageId());
      log.error("订单ID: {}", orderMessage.getOrderId());
      log.error("用户ID: {}", orderMessage.getUserId());
      log.error("优惠券ID: {}", orderMessage.getVoucherId());
      log.error("重试次数: {}", orderMessage.getRetryCount());
      log.error("创建时间: {}", orderMessage.getCreateTime());
      log.error("请及时查看并处理此订单！");
      log.error("🚨🚨🚨 END ALERT 🚨🚨🚨");

      // TODO: 集成实际的通知服务
      // emailService.sendAdminAlert(orderMessage);
      // smsService.sendAdminAlert(orderMessage);
      // dingTalkService.sendAdminAlert(orderMessage);

    } catch (Exception e) {
      log.error("发送管理员通知失败", e);
    }
  }

  /**
   * 清理已处理的消息记录（定期清理，避免内存泄漏）
   */
  public void cleanProcessedMessages() {
    if (processedMessages.size() > 10000) {
      processedMessages.clear();
      log.info("清理已处理消息记录，当前大小: {}", processedMessages.size());
    }
  }
}
