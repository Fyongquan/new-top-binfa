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
        // 如果订单已存在且状态为处理中，更新为成功（状态机模式）
        if (existingOrder.getStatus() == Order.STATUS_PROCESSING) {
          boolean updated = updateOrderStatusWithPreviousCheck(
              existingOrder.getId(),
              Order.STATUS_SUCCESS,
              Order.STATUS_PROCESSING);
          if (updated) {
            seckillService.orderSuccess(orderId);
          }
        }
        return true;
      }

      // 创建新订单
      Order order = new Order();
      order.setId(orderId);
      order.setUserId(userId);
      order.setVoucherId(voucherId);
      order.setStatus(Order.STATUS_PROCESSING); // 处理中
      order.setCreateTime(LocalDateTime.now());
      order.setUpdateTime(LocalDateTime.now());

      int result = orderMapper.insert(order);

      if (result > 0) {
        // 订单创建成功，使用状态机更新为成功状态
        boolean updated = updateOrderStatusWithPreviousCheck(
            orderId,
            Order.STATUS_SUCCESS,
            Order.STATUS_PROCESSING);

        if (updated) {
          // 通知订单成功
          seckillService.orderSuccess(orderId);
          log.info("订单创建成功 - 用户: {}, 优惠券: {}, 订单: {}", userId, voucherId, orderId);
          return true;
        } else {
          log.error("订单状态更新失败 - 用户: {}, 优惠券: {}, 订单: {}", userId, voucherId, orderId);
          return false;
        }
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
   * 使用状态机模式更新订单状态（带前置状态校验）
   * 这是幂等性保证的关键方法
   * 
   * @param orderId               订单ID
   * @param newStatus             新状态
   * @param expectedCurrentStatus 期望的当前状态
   * @return 是否更新成功
   */
  @Transactional(rollbackFor = Exception.class)
  public boolean updateOrderStatusWithPreviousCheck(Long orderId, Integer newStatus, Integer expectedCurrentStatus) {
    try {
      // 使用状态机模式更新：UPDATE order SET status = newStatus WHERE id = orderId AND status =
      // expectedCurrentStatus
      int result = orderMapper.updateStatusWithPreviousCheck(orderId, newStatus, expectedCurrentStatus);

      if (result > 0) {
        log.info("📋 订单状态机更新成功 - 订单: {}, 状态: {} -> {}", orderId,
            getStatusName(expectedCurrentStatus), getStatusName(newStatus));
        return true;
      } else {
        log.warn("📋 订单状态机更新失败 - 订单: {}, 期望状态: {}, 目标状态: {} (可能状态已变更)",
            orderId, getStatusName(expectedCurrentStatus), getStatusName(newStatus));
        return false;
      }
    } catch (Exception e) {
      log.error("📋 状态机更新订单状态异常 - 订单: {}, 目标状态: {}", orderId, getStatusName(newStatus), e);
      throw e;
    }
  }

  /**
   * 更新订单状态（不推荐直接使用，优先使用状态机方法）
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
        log.info("订单状态更新成功 - 订单: {}, 状态: {}", orderId, getStatusName(status));
        return true;
      } else {
        log.warn("订单状态更新失败 - 订单: {}, 状态: {}", orderId, getStatusName(status));
        return false;
      }
    } catch (Exception e) {
      log.error("更新订单状态异常 - 订单: {}, 状态: {}", orderId, getStatusName(status), e);
      throw e;
    }
  }

  /**
   * 获取状态名称（用于日志显示）
   * 
   * @param status 状态码
   * @return 状态名称
   */
  private String getStatusName(Integer status) {
    if (status == null)
      return "NULL";
    switch (status) {
      case Order.STATUS_PROCESSING:
        return "处理中";
      case Order.STATUS_SUCCESS:
        return "成功";
      case Order.STATUS_FAILED:
        return "失败";
      default:
        return "未知(" + status + ")";
    }
  }
}
