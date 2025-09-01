package com.seckill.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis操作服务类
 * 
 * @author seckill-test
 */
@Slf4j
@Service
public class RedisService {

  @Resource
  private RedisTemplate<String, Object> redisTemplate;

  @Resource
  @Qualifier("seckillScript")
  private DefaultRedisScript<Long> seckillScript;

  @Resource
  @Qualifier("recoverStockScript")
  private DefaultRedisScript<Long> recoverStockScript;

  /**
   * 初始化秒杀库存
   * 
   * @param voucherId 优惠券ID
   * @param stock     库存数量
   */
  public void initStock(Long voucherId, Integer stock) {
    String stockKey = "seckill:stock:" + voucherId;
    String orderKey = "seckill:order:" + voucherId;

    // 存储为字符串，确保Lua脚本能正确读取，设置24小时过期时间
    redisTemplate.opsForValue().set(stockKey, stock.toString(), 24, TimeUnit.HOURS);
    // 清理旧的购买记录
    redisTemplate.delete(orderKey);
    // 为用户购买记录设置过期时间（25小时，比库存稍长一些）
    redisTemplate.expire(orderKey, 25, TimeUnit.HOURS);

    log.info("初始化优惠券{}库存: {} (TTL: 24小时)", voucherId, stock);
  }

  /**
   * 执行秒杀Lua脚本
   * 
   * @param voucherId 优惠券ID
   * @param userId    用户ID
   * @param limit     限购数量
   * @return 0-成功, 1-库存不足, 2-超过限购
   */
  public Long executeSeckill(Long voucherId, Long userId, Integer limit) {
    try {
      // 正确传递KEYS参数
      java.util.List<String> keys = java.util.Arrays.asList(
          "seckill:stock:" + voucherId, // KEYS[1] - 库存键
          "seckill:order:" + voucherId // KEYS[2] - 订单键
      );

      log.info("🔍 执行秒杀脚本 - 用户: {}, 优惠券: {}, KEYS: {}", userId, voucherId, keys);

      Long result = redisTemplate.execute(
          seckillScript,
          keys, // 传递键名列表
          voucherId.toString(), // ARGV[1]
          userId.toString(), // ARGV[2]
          limit.toString()); // ARGV[3]

      log.info("秒杀脚本执行结果 - 用户: {}, 优惠券: {}, 结果: {}", userId, voucherId, result);
      return result;
    } catch (Exception e) {
      log.error("执行秒杀脚本异常", e);
      return -1L; // 脚本执行异常
    }
  }

  /**
   * 执行库存回滚Lua脚本
   * 
   * @param voucherId 优惠券ID
   * @param userId    用户ID
   * @return 0-成功
   */
  public Long executeStockRollback(Long voucherId, Long userId) {
    try {
      // 正确传递KEYS参数
      java.util.List<String> keys = java.util.Arrays.asList(
          "seckill:stock:" + voucherId, // KEYS[1] - 库存键
          "seckill:order:" + voucherId // KEYS[2] - 订单键
      );

      Long result = redisTemplate.execute(
          recoverStockScript,
          keys, // 传递键名列表
          voucherId.toString(), // ARGV[1]
          userId.toString()); // ARGV[2]

      log.info("库存回滚脚本执行结果 - 用户: {}, 优惠券: {}, 结果: {}", userId, voucherId, result);
      return result;
    } catch (Exception e) {
      log.error("执行库存回滚脚本异常", e);
      return -1L; // 脚本执行异常
    }
  }

  /**
   * 获取当前库存
   * 
   * @param voucherId 优惠券ID
   * @return 库存数量
   */
  public Integer getCurrentStock(Long voucherId) {
    String stockKey = "seckill:stock:" + voucherId;
    Object stock = redisTemplate.opsForValue().get(stockKey);
    if (stock == null) {
      return 0;
    }
    try {
      return Integer.parseInt(stock.toString());
    } catch (NumberFormatException e) {
      log.warn("库存数据格式异常: {}", stock);
      return 0;
    }
  }

  /**
   * 获取用户购买数量
   * 
   * @param voucherId 优惠券ID
   * @param userId    用户ID
   * @return 购买数量
   */
  public Integer getUserBoughtCount(Long voucherId, Long userId) {
    String orderKey = "seckill:order:" + voucherId;
    Object count = redisTemplate.opsForHash().get(orderKey, userId.toString());
    return count != null ? Integer.parseInt(count.toString()) : 0;
  }

  /**
   * 设置订单状态缓存
   * 
   * @param orderId       订单ID
   * @param status        订单状态
   * @param expireSeconds 过期时间（秒）
   */
  public void setOrderStatus(Long orderId, Integer status, long expireSeconds) {
    String orderStatusKey = "order:status:" + orderId;
    redisTemplate.opsForValue().set(orderStatusKey, status.toString(), expireSeconds, TimeUnit.SECONDS);
  }

  /**
   * 获取订单状态
   * 
   * @param orderId 订单ID
   * @return 订单状态
   */
  public Integer getOrderStatus(Long orderId) {
    String orderStatusKey = "order:status:" + orderId;
    Object status = redisTemplate.opsForValue().get(orderStatusKey);
    return status != null ? Integer.parseInt(status.toString()) : null;
  }

  /**
   * 设置秒杀活动过期时间
   * 
   * @param voucherId     优惠券ID
   * @param expireSeconds 过期时间（秒）
   */
  public void setSeckillExpire(Long voucherId, long expireSeconds) {
    String stockKey = "seckill:stock:" + voucherId;
    String orderKey = "seckill:order:" + voucherId;

    redisTemplate.expire(stockKey, expireSeconds, TimeUnit.SECONDS);
    redisTemplate.expire(orderKey, expireSeconds + 3600, TimeUnit.SECONDS); // 购买记录比库存多保存1小时

    log.info("设置优惠券{}过期时间: {}秒", voucherId, expireSeconds);
  }

  /**
   * 获取秒杀数据剩余过期时间
   * 
   * @param voucherId 优惠券ID
   * @return 剩余时间（秒），-1表示没有过期时间，-2表示key不存在
   */
  public Long getSeckillTTL(Long voucherId) {
    String stockKey = "seckill:stock:" + voucherId;
    return redisTemplate.getExpire(stockKey, TimeUnit.SECONDS);
  }

  /**
   * 清理过期的秒杀数据
   * 
   * @param voucherId 优惠券ID
   */
  public void cleanExpiredSeckillData(Long voucherId) {
    String stockKey = "seckill:stock:" + voucherId;
    String orderKey = "seckill:order:" + voucherId;
    String timeKey = "seckill:time:" + voucherId;

    redisTemplate.delete(stockKey);
    redisTemplate.delete(orderKey);
    redisTemplate.delete(timeKey);

    log.info("清理优惠券{}的过期秒杀数据", voucherId);
  }

  /**
   * 获取回滚日志（用于监控）
   * 
   * @param count 获取数量
   * @return 回滚日志列表
   */
  public Object getRollbackLogs(long count) {
    return redisTemplate.opsForList().range("seckill:rollback:log", 0, count - 1);
  }

  /**
   * 调试方法：获取Redis中的调试信息
   * 
   * @param voucherId 优惠券ID
   */
  public void debugRedisData(Long voucherId) {
    String stockKey = "seckill:stock:" + voucherId;
    String debugKey = "seckill:debug:" + voucherId;

    // 直接读取库存数据
    Object stockData = redisTemplate.opsForValue().get(stockKey);
    log.info("🔍 调试Redis数据 - 优惠券: {}", voucherId);
    log.info("📦 库存键: {}", stockKey);
    log.info("📦 库存原始数据: {} (类型: {})", stockData, stockData != null ? stockData.getClass().getSimpleName() : "null");

    // 读取Lua脚本的详细调试信息
    Object stockKeyUsed = redisTemplate.opsForHash().get(debugKey, "stockKey");
    Object keyExists = redisTemplate.opsForHash().get(debugKey, "key_exists");
    Object rawStock = redisTemplate.opsForHash().get(debugKey, "raw_stock");
    Object parsedStock = redisTemplate.opsForHash().get(debugKey, "parsed_stock");
    Object error = redisTemplate.opsForHash().get(debugKey, "error");

    log.info("🐛 === Lua脚本详细调试信息 ===");
    log.info("🐛 脚本中使用的库存键: {}", stockKeyUsed);
    log.info("🐛 键是否存在(EXISTS): {}", keyExists);
    log.info("🐛 GET命令返回值: {}", rawStock);
    log.info("🐛 tonumber解析结果: {}", parsedStock);
    log.info("🐛 错误信息: {}", error);
    log.info("🐛 =============================");
  }

}
