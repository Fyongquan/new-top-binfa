package com.seckill.service;

import com.seckill.dto.OrderMessage;
import com.seckill.dto.SeckillRequest;
import com.seckill.dto.SeckillResponse;
import com.seckill.mq.producer.OrderProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 秒杀核心业务服务
 * 
 * @author seckill-test
 */
@Slf4j
@Service
public class SeckillService {

  @Resource
  private RedisService redisService;

  @Resource
  private OrderProducer orderProducer;

  /**
   * 执行秒杀
   * 
   * @param request 秒杀请求
   * @return 秒杀响应
   */
  public SeckillResponse doSeckill(SeckillRequest request) {
    Long userId = request.getUserId();
    Long voucherId = request.getVoucherId();
    Integer limit = request.getLimit();

    try {
      // 1. 执行Redis Lua脚本进行库存检查和扣减
      Long result = redisService.executeSeckill(voucherId, userId, limit);

      // 2. 根据脚本执行结果返回响应
      switch (result.intValue()) {
        case 0: // 成功
          // 生成订单ID
          Long orderId = generateOrderId();

          // 设置订单初始状态为处理中
          redisService.setOrderStatus(orderId, 0, 300); // 5分钟过期

          // 发送异步消息到MQ进行订单处理
          sendOrderMessage(userId, voucherId, orderId);

          log.info("秒杀成功 - 用户: {}, 优惠券: {}, 订单: {}", userId, voucherId, orderId);
          return SeckillResponse.success(orderId);

        case 1: // 库存不足
          log.warn("秒杀失败-库存不足 - 用户: {}, 优惠券: {}", userId, voucherId);
          return SeckillResponse.stockNotEnough();

        case 2: // 超过限购
          log.warn("秒杀失败-超过限购 - 用户: {}, 优惠券: {}", userId, voucherId);
          return SeckillResponse.limitExceeded();

        default: // 脚本执行异常
          log.error("秒杀失败-脚本执行异常 - 用户: {}, 优惠券: {}, 结果: {}", userId, voucherId, result);
          return SeckillResponse.systemError("Redis脚本执行异常");
      }

    } catch (Exception e) {
      log.error("秒杀服务异常 - 用户: {}, 优惠券: {}", userId, voucherId, e);
      return SeckillResponse.systemError(e.getMessage());
    }
  }

  /**
   * 查询订单状态
   * 
   * @param orderId 订单ID
   * @return 订单状态: 0-处理中, 1-成功, 2-失败, null-订单不存在
   */
  public Integer getOrderStatus(Long orderId) {
    return redisService.getOrderStatus(orderId);
  }

  /**
   * 初始化秒杀活动
   * 
   * @param voucherId 优惠券ID
   * @param stock     库存数量
   */
  public void initSeckillActivity(Long voucherId, Integer stock) {
    redisService.initStock(voucherId, stock);
    log.info("初始化秒杀活动 - 优惠券: {}, 库存: {}", voucherId, stock);
  }

  /**
   * 获取当前库存信息
   * 
   * @param voucherId 优惠券ID
   * @return 库存数量
   */
  public Integer getCurrentStock(Long voucherId) {
    return redisService.getCurrentStock(voucherId);
  }

  /**
   * 获取用户购买数量
   * 
   * @param voucherId 优惠券ID
   * @param userId    用户ID
   * @return 购买数量
   */
  public Integer getUserBoughtCount(Long voucherId, Long userId) {
    return redisService.getUserBoughtCount(voucherId, userId);
  }

  /**
   * 处理订单失败后的库存回滚
   * 
   * @param voucherId 优惠券ID
   * @param userId    用户ID
   * @param orderId   订单ID
   */
  public void rollbackStock(Long voucherId, Long userId, Long orderId) {
    try {
      // 执行库存回滚
      Long result = redisService.executeStockRollback(voucherId, userId);

      if (result == 0) {
        // 更新订单状态为失败
        redisService.setOrderStatus(orderId, 2, 300);
        log.info("库存回滚成功 - 用户: {}, 优惠券: {}, 订单: {}", userId, voucherId, orderId);
      } else {
        log.error("库存回滚失败 - 用户: {}, 优惠券: {}, 订单: {}, 结果: {}", userId, voucherId, orderId, result);
      }
    } catch (Exception e) {
      log.error("库存回滚异常 - 用户: {}, 优惠券: {}, 订单: {}", userId, voucherId, orderId, e);
    }
  }

  /**
   * 处理订单成功
   * 
   * @param orderId 订单ID
   */
  public void orderSuccess(Long orderId) {
    // 更新订单状态为成功
    redisService.setOrderStatus(orderId, 1, 300);
    log.info("订单处理成功 - 订单: {}", orderId);
  }

  /**
   * 生成订单ID
   * 
   * @return 订单ID
   */
  private Long generateOrderId() {
    // 简单的时间戳 + 随机数生成订单ID
    // 实际项目中可以使用雪花算法等更复杂的ID生成策略
    return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
  }

  /**
   * 发送订单消息到MQ
   * 
   * @param userId    用户ID
   * @param voucherId 优惠券ID
   * @param orderId   订单ID
   */
  private void sendOrderMessage(Long userId, Long voucherId, Long orderId) {
    OrderMessage message = new OrderMessage();
    message.setMessageId(UUID.randomUUID().toString());
    message.setUserId(userId);
    message.setVoucherId(voucherId);
    message.setOrderId(orderId);
    message.setCreateTime(LocalDateTime.now());

    orderProducer.sendOrderMessage(message);
  }
}
