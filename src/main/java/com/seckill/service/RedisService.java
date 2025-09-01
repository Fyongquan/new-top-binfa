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

  @Resource
  @Qualifier("testScript")
  private DefaultRedisScript<String> testScript;

  @Resource
  @Qualifier("seckillDebugScript")
  private DefaultRedisScript<Long> seckillDebugScript;

  /**
   * 初始化秒杀库存
   * 
   * @param voucherId 优惠券ID
   * @param stock     库存数量
   */
  public void initStock(Long voucherId, Integer stock) {
    String stockKey = "seckill:stock:" + voucherId;
    String orderKey = "seckill:order:" + voucherId;

    // 存储为字符串，确保Lua脚本能正确读取
    redisTemplate.opsForValue().set(stockKey, stock.toString());
    // 清理旧的购买记录
    redisTemplate.delete(orderKey);

    log.info("初始化优惠券{}库存: {}", voucherId, stock);
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

      // 立即查看脚本执行后的调试信息
      debugRedisData(voucherId);

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
    redisTemplate.opsForValue().set(orderStatusKey, status, expireSeconds, TimeUnit.SECONDS);
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

    // 读取Lua脚本的调试信息
    Object rawStock = redisTemplate.opsForHash().get(debugKey, "raw_stock");
    Object parsedStock = redisTemplate.opsForHash().get(debugKey, "parsed_stock");
    log.info("📦 Lua脚本读取的原始值: {}", rawStock);
    log.info("📦 Lua脚本解析后的值: {}", parsedStock);
  }

  /**
   * 测试KEYS参数传递是否正确
   * 
   * @return 测试结果
   */
  public String testKeysParameter() {
    try {
      String testKey = "test:keys:validation";
      String testValue = "SUCCESS";

      java.util.List<String> keys = java.util.Arrays.asList(testKey);

      log.info("🧪 测试KEYS参数传递 - KEY: {}, VALUE: {}", testKey, testValue);

      String result = redisTemplate.execute(
          testScript,
          keys, // 传递KEYS参数
          testValue); // ARGV[1]

      log.info("🧪 KEYS参数测试结果: {}", result);

      return result;
    } catch (Exception e) {
      log.error("🧪 KEYS参数测试失败", e);
      return "ERROR";
    }
  }

  /**
   * 执行调试版本的秒杀脚本
   * 
   * @param voucherId 优惠券ID
   * @param userId    用户ID
   * @param limit     限购数量
   * @return 执行结果
   */
  public Long executeSeckillDebug(Long voucherId, Long userId, Integer limit) {
    try {
      java.util.List<String> keys = java.util.Arrays.asList(
          "seckill:stock:" + voucherId, // KEYS[1]
          "seckill:order:" + voucherId // KEYS[2]
      );

      log.info("🐛 执行调试秒杀脚本 - 用户: {}, 优惠券: {}, KEYS: {}", userId, voucherId, keys);

      Long result = redisTemplate.execute(
          seckillDebugScript,
          keys,
          voucherId.toString(),
          userId.toString(),
          limit.toString());

      log.info("🐛 调试秒杀脚本结果: {}", result);

      // 读取详细的调试信息
      showSeckillDebugInfo(voucherId);

      return result;
    } catch (Exception e) {
      log.error("🐛 调试秒杀脚本异常", e);
      return -1L;
    }
  }

  /**
   * 显示秒杀调试信息
   * 
   * @param voucherId 优惠券ID
   */
  private void showSeckillDebugInfo(Long voucherId) {
    String debugKey = "seckill:debug:" + voucherId;
    java.util.Map<Object, Object> debugInfo = redisTemplate.opsForHash().entries(debugKey);

    log.info("🐛 ========== 秒杀调试信息 ==========");
    log.info("🐛 优惠券ID: {}", voucherId);

    for (java.util.Map.Entry<Object, Object> entry : debugInfo.entrySet()) {
      log.info("🐛 {}: {}", entry.getKey(), entry.getValue());
    }

    log.info("🐛 ====================================");
  }
}
