package com.seckill.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 秒杀响应DTO
 * 
 * @author seckill-test
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillResponse {

  /**
   * 响应码: 0-成功, 1-库存不足, 2-超过限购, 3-系统异常
   */
  private Integer code;

  /**
   * 响应消息
   */
  private String message;

  /**
   * 订单ID（成功时返回）
   */
  private Long orderId;

  /**
   * 时间戳
   */
  private Long timestamp;

  /**
   * 成功响应
   */
  public static SeckillResponse success(Long orderId) {
    return new SeckillResponse(0, "秒杀成功，正在为您生成订单...", orderId, System.currentTimeMillis());
  }

  /**
   * 库存不足响应
   */
  public static SeckillResponse stockNotEnough() {
    return new SeckillResponse(1, "库存不足，秒杀失败", null, System.currentTimeMillis());
  }

  /**
   * 超过限购响应
   */
  public static SeckillResponse limitExceeded() {
    return new SeckillResponse(2, "超过个人限购数量", null, System.currentTimeMillis());
  }

  /**
   * 系统异常响应
   */
  public static SeckillResponse systemError(String message) {
    return new SeckillResponse(3, "系统异常: " + message, null, System.currentTimeMillis());
  }
}
