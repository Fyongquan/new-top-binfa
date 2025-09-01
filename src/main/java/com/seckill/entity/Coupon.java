package com.seckill.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 优惠券实体类
 * 
 * @author seckill-test
 */
@Data
public class Coupon {

  /**
   * 优惠券ID
   */
  private Long id;

  /**
   * 优惠券名称
   */
  private String name;

  /**
   * 当前库存
   */
  private Integer stock;

  /**
   * 总库存
   */
  private Integer totalStock;

  /**
   * 开始时间
   */
  private LocalDateTime startTime;

  /**
   * 结束时间
   */
  private LocalDateTime endTime;

  /**
   * 创建时间
   */
  private LocalDateTime createTime;

  /**
   * 更新时间
   */
  private LocalDateTime updateTime;
}
