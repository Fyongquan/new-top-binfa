package com.seckill.mq.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.dto.OrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import jakarta.annotation.PostConstruct;

/**
 * 订单消息生产者
 * 
 * @author seckill-test
 */
@Slf4j
@Component
public class OrderProducer {

  @Resource
  private RabbitTemplate rabbitTemplate;

  @Resource
  private ObjectMapper objectMapper;

  private static final String ORDER_EXCHANGE = "seckill.order.exchange";
  private static final String ORDER_ROUTING_KEY = "seckill.order.create";

  /**
   * 初始化RabbitTemplate配置
   */
  @PostConstruct
  public void initRabbitTemplate() {
    // 设置全局消息确认回调（只设置一次）
    rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
      String messageId = correlationData != null ? correlationData.getId() : "unknown";
      if (ack) {
        log.info("订单消息发送成功 - 消息ID: {}", messageId);
      } else {
        log.error("订单消息发送失败 - 消息ID: {}, 原因: {}", messageId, cause);
      }
    });
  }

  /**
   * 发送订单消息
   * 
   * @param orderMessage 订单消息
   */
  public void sendOrderMessage(OrderMessage orderMessage) {
    try {
      // 创建CorrelationData用于消息确认回调
      CorrelationData correlationData = new CorrelationData(orderMessage.getMessageId());

      // 发送消息，带上correlationData
      rabbitTemplate.convertAndSend(ORDER_EXCHANGE, ORDER_ROUTING_KEY, orderMessage, correlationData);

      log.info("订单消息已投递到MQ - 消息ID: {}, 用户: {}, 优惠券: {}, 订单: {}",
          orderMessage.getMessageId(), orderMessage.getUserId(),
          orderMessage.getVoucherId(), orderMessage.getOrderId());

    } catch (Exception e) {
      log.error("发送订单消息异常 - 消息ID: {}, 订单: {}",
          orderMessage.getMessageId(), orderMessage.getOrderId(), e);
      throw new RuntimeException("订单消息发送失败", e);
    }
  }

  /**
   * 发送延迟重试消息
   * 
   * @param orderMessage 订单消息
   * @param delaySeconds 延迟秒数
   */
  public void sendDelayRetryMessage(OrderMessage orderMessage, int delaySeconds) {
    try {
      orderMessage.incrementRetry();

      // 发送到延迟队列
      rabbitTemplate.convertAndSend(
          "seckill.order.delay.exchange",
          "seckill.order.delay",
          orderMessage,
          message -> {
            message.getMessageProperties().setExpiration(String.valueOf(delaySeconds * 1000));
            return message;
          });

      log.info("延迟重试消息已发送 - 消息ID: {}, 订单: {}, 重试次数: {}, 延迟: {}秒",
          orderMessage.getMessageId(), orderMessage.getOrderId(),
          orderMessage.getRetryCount(), delaySeconds);

    } catch (Exception e) {
      log.error("发送延迟重试消息异常 - 消息ID: {}, 订单: {}",
          orderMessage.getMessageId(), orderMessage.getOrderId(), e);
    }
  }
}
