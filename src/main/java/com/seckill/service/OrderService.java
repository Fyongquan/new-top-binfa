package com.seckill.service;

import com.seckill.entity.Order;
import com.seckill.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 订单处理服务
 * 
 * @author seckill-test
 */
@Slf4j
@Service
public class OrderService {

  @Resource
  private OrderMapper orderMapper;

  @Resource
  private SeckillService seckillService;

  /**
   * 创建订单
   * 
   * @param userId    用户ID
   * @param voucherId 优惠券ID
   * @param orderId   订单ID
   * @return 是否创建成功
   */
  @Transactional(rollbackFor = Exception.class)
  public boolean createOrder(Long userId, Long voucherId, Long orderId) {
    try {
      // 检查是否已经存在相同的订单（幂等性保证）
      Order existingOrder = orderMapper.findByUserIdAndVoucherId(userId, voucherId);
      if (existingOrder != null) {
        log.warn("订单已存在，跳过创建 - 用户: {}, 优惠券: {}", userId, voucherId);
        // 如果订单已存在且状态为处理中，更新为成功
        if (existingOrder.getStatus() == 0) {
          existingOrder.setStatus(1);
          existingOrder.setUpdateTime(LocalDateTime.now());
          orderMapper.updateById(existingOrder);
          seckillService.orderSuccess(orderId);
        }
        return true;
      }

      // 创建新订单
      Order order = new Order();
      order.setId(orderId);
      order.setUserId(userId);
      order.setVoucherId(voucherId);
      order.setStatus(0); // 处理中
      order.setCreateTime(LocalDateTime.now());
      order.setUpdateTime(LocalDateTime.now());

      int result = orderMapper.insert(order);

      if (result > 0) {
        // 订单创建成功，更新状态为成功
        order.setStatus(1);
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);

        // 通知订单成功
        seckillService.orderSuccess(orderId);

        log.info("订单创建成功 - 用户: {}, 优惠券: {}, 订单: {}", userId, voucherId, orderId);
        return true;
      } else {
        log.error("订单创建失败 - 用户: {}, 优惠券: {}, 订单: {}", userId, voucherId, orderId);
        return false;
      }

    } catch (Exception e) {
      log.error("创建订单异常 - 用户: {}, 优惠券: {}, 订单: {}", userId, voucherId, orderId, e);
      throw e; // 重新抛出异常，触发事务回滚
    }
  }

  /**
   * 根据订单ID查询订单
   * 
   * @param orderId 订单ID
   * @return 订单信息
   */
  public Order getOrderById(Long orderId) {
    return orderMapper.selectById(orderId);
  }

  /**
   * 根据用户ID和优惠券ID查询订单
   * 
   * @param userId    用户ID
   * @param voucherId 优惠券ID
   * @return 订单信息
   */
  public Order getOrderByUserIdAndVoucherId(Long userId, Long voucherId) {
    return orderMapper.findByUserIdAndVoucherId(userId, voucherId);
  }

  /**
   * 更新订单状态
   * 
   * @param orderId 订单ID
   * @param status  新状态
   * @return 是否更新成功
   */
  @Transactional(rollbackFor = Exception.class)
  public boolean updateOrderStatus(Long orderId, Integer status) {
    try {
      Order order = new Order();
      order.setId(orderId);
      order.setStatus(status);
      order.setUpdateTime(LocalDateTime.now());

      int result = orderMapper.updateById(order);

      if (result > 0) {
        log.info("订单状态更新成功 - 订单: {}, 状态: {}", orderId, status);
        return true;
      } else {
        log.warn("订单状态更新失败 - 订单: {}, 状态: {}", orderId, status);
        return false;
      }
    } catch (Exception e) {
      log.error("更新订单状态异常 - 订单: {}, 状态: {}", orderId, status, e);
      throw e;
    }
  }
}
