package com.seckill.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 秒杀请求DTO
 * 
 * @author seckill-test
 */
@Data
public class SeckillRequest {

  /**
   * 用户ID
   */
  @NotNull(message = "用户ID不能为空")
  @Positive(message = "用户ID必须为正数")
  private Long userId;

  /**
   * 优惠券ID
   */
  @NotNull(message = "优惠券ID不能为空")
  @Positive(message = "优惠券ID必须为正数")
  private Long voucherId;

  /**
   * 限购数量，默认为1
   */
  @Positive(message = "限购数量必须为正数")
  private Integer limit = 1;
}
