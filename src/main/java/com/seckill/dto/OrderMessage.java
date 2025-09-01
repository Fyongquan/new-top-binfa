package com.seckill.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 订单消息DTO - 用于MQ消息传递
 * 
 * @author seckill-test
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderMessage {

  /**
   * 消息唯一ID（用于幂等性）
   */
  private String messageId;

  /**
   * 用户ID
   */
  private Long userId;

  /**
   * 优惠券ID
   */
  private Long voucherId;

  /**
   * 订单ID
   */
  private Long orderId;

  /**
   * 创建时间
   */
  private LocalDateTime createTime;

  /**
   * 重试次数
   */
  private Integer retryCount = 0;

  /**
   * 最大重试次数
   */
  private static final Integer MAX_RETRY_COUNT = 3;

  /**
   * 是否可以重试
   */
  public boolean canRetry() {
    return retryCount < MAX_RETRY_COUNT;
  }

  /**
   * 增加重试次数
   */
  public void incrementRetry() {
    this.retryCount++;
  }
}
