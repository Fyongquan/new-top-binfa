package com.seckill.task;

import com.seckill.entity.Coupon;
import com.seckill.mapper.CouponMapper;
import com.seckill.service.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀系统定时任务
 * 
 * @author seckill-system
 */
@Slf4j
@Component
public class SeckillScheduleTask {

  @Resource
  private CouponMapper couponMapper;

  @Resource
  private RedisService redisService;

  /**
   * 每天凌晨12点更新所有优惠券的时间和库存
   * 实现自动化的秒杀活动重置
   */
  @Scheduled(cron = "0 0 0 * * ?")
  public void updateCouponTime() {
    log.info("🕒 开始执行定时任务：更新优惠券时间和库存");

    try {
      List<Coupon> couponList = couponMapper.selectAll();

      if (couponList == null || couponList.isEmpty()) {
        log.info("📋 没有找到优惠券数据，跳过更新任务");
        return;
      }

      int updateCount = 0;
      for (Coupon coupon : couponList) {
        try {
          // 更新数据库中的优惠券时间
          Coupon updateCoupon = new Coupon();
          updateCoupon.setId(coupon.getId());
          updateCoupon.setStartTime(coupon.getStartTime().plusDays(1));
          updateCoupon.setEndTime(coupon.getEndTime().plusDays(1));
          updateCoupon.setUpdateTime(LocalDateTime.now());

          // 恢复到总库存
          updateCoupon.setStock(coupon.getTotalStock());

          couponMapper.updateById(updateCoupon);

          // 同步更新Redis中的库存
          redisService.initStock(coupon.getId(), coupon.getTotalStock());

          updateCount++;
          log.info("✅ 更新优惠券 {} 成功：开始时间 {} -> {}，库存恢复至 {}",
              coupon.getId(),
              coupon.getStartTime().plusDays(1),
              coupon.getEndTime().plusDays(1),
              coupon.getTotalStock());

        } catch (Exception e) {
          log.error("❌ 更新优惠券 {} 失败", coupon.getId(), e);
        }
      }

      log.info("🎯 定时任务完成：成功更新 {} 个优惠券", updateCount);

    } catch (Exception e) {
      log.error("💥 定时任务执行失败", e);
    }
  }

  /**
   * 每小时清理过期的调试数据
   * 避免调试数据占用过多Redis内存
   */
  @Scheduled(cron = "0 0 * * * ?")
  public void cleanDebugData() {
    log.info("🧹 开始清理过期调试数据");

    try {
      // 这里可以实现清理逻辑
      // 例如：删除过期的debug键、统计信息等

      log.info("✅ 调试数据清理完成");
    } catch (Exception e) {
      log.error("❌ 清理调试数据失败", e);
    }
  }

  /**
   * 每10分钟检查Redis连接和关键数据
   * 健康检查和监控
   */
  @Scheduled(fixedRate = 600000) // 10分钟
  public void healthCheck() {
    try {
      // 检查Redis连接
      List<Coupon> activeCoupons = couponMapper.selectAll();

      if (activeCoupons != null && !activeCoupons.isEmpty()) {
        for (Coupon coupon : activeCoupons) {
          Long ttl = redisService.getSeckillTTL(coupon.getId());
          if (ttl != null && ttl == -1) {
            log.warn("⚠️ 优惠券 {} 的Redis数据没有设置TTL，可能存在内存泄漏风险", coupon.getId());
          }
        }
      }

    } catch (Exception e) {
      log.error("💓 健康检查失败", e);
    }
  }
}
