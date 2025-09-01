package com.seckill.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 订单实体类
 * 
 * @author seckill-test
 */
@Data
public class Order {

  /**
   * 订单ID
   */
  private Long id;

  /**
   * 用户ID
   */
  private Long userId;

  /**
   * 优惠券ID
   */
  private Long voucherId;

  /**
   * 订单状态: 0-处理中, 1-成功, 2-失败
   */
  private Integer status;

  /**
   * 创建时间
   */
  private LocalDateTime createTime;

  /**
   * 更新时间
   */
  private LocalDateTime updateTime;
}
