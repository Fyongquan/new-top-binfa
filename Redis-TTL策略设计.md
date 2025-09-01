# 🕐 Redis TTL 策略设计指南

## 📋 问题背景

在高并发秒杀系统中，Redis 数据的生命周期管理至关重要。如果不设置合理的过期时间（TTL），会导致：

- **内存泄漏**：Redis 内存无限增长，最终导致 OOM
- **性能下降**：大量过期数据影响 Redis 性能
- **业务逻辑混乱**：过期活动的数据仍然存在

## 🎯 TTL 策略设计

### 1. 分层 TTL 架构

| 数据类型         | TTL 时间       | 设计理由               | 示例 Key              |
| ---------------- | -------------- | ---------------------- | --------------------- |
| **库存数据**     | 24 小时        | 对应秒杀活动周期       | `seckill:stock:1001`  |
| **用户购买记录** | 25 小时        | 比库存稍长，防重复购买 | `seckill:order:1001`  |
| **订单状态缓存** | 30 分钟-2 小时 | 根据订单处理时长       | `order:status:123456` |
| **调试信息**     | 1 小时         | 临时调试数据           | `seckill:debug:1001`  |

### 2. TTL 设计原则

#### 🎯 业务对齐原则

```java
// 秒杀活动24小时，库存数据24小时过期
redisTemplate.opsForValue().set(stockKey, stock.toString(), 24, TimeUnit.HOURS);
```

#### 🔄 层次化设计

```java
// 用户购买记录比库存多保存1小时，确保业务逻辑正确性
redisTemplate.expire(orderKey, 25, TimeUnit.HOURS);
```

#### 📊 监控友好

```java
// 可以通过TTL监控活动剩余时间
public Long getSeckillTTL(Long voucherId) {
    String stockKey = "seckill:stock:" + voucherId;
    return redisTemplate.getExpire(stockKey, TimeUnit.SECONDS);
}
```

## 🛠️ 实现方案

### 核心实现代码

```java
/**
 * 初始化秒杀库存 - 带TTL设置
 */
public void initStock(Long voucherId, Integer stock) {
    String stockKey = "seckill:stock:" + voucherId;
    String orderKey = "seckill:order:" + voucherId;

    // 库存数据24小时过期
    redisTemplate.opsForValue().set(stockKey, stock.toString(), 24, TimeUnit.HOURS);

    // 清理旧的购买记录，并设置过期时间
    redisTemplate.delete(orderKey);
    redisTemplate.expire(orderKey, 25, TimeUnit.HOURS);

    log.info("初始化优惠券{}库存: {} (TTL: 24小时)", voucherId, stock);
}

/**
 * 动态设置秒杀活动过期时间
 */
public void setSeckillExpire(Long voucherId, long expireSeconds) {
    String stockKey = "seckill:stock:" + voucherId;
    String orderKey = "seckill:order:" + voucherId;

    redisTemplate.expire(stockKey, expireSeconds, TimeUnit.SECONDS);
    redisTemplate.expire(orderKey, expireSeconds + 3600, TimeUnit.SECONDS);

    log.info("设置优惠券{}过期时间: {}秒", voucherId, expireSeconds);
}
```

### TTL 监控方法

```java
/**
 * 获取秒杀数据剩余过期时间
 */
public Long getSeckillTTL(Long voucherId) {
    String stockKey = "seckill:stock:" + voucherId;
    Long ttl = redisTemplate.getExpire(stockKey, TimeUnit.SECONDS);

    if (ttl == -1) {
        log.warn("秒杀数据{}没有设置过期时间!", voucherId);
    } else if (ttl == -2) {
        log.info("秒杀数据{}不存在", voucherId);
    } else {
        log.info("秒杀数据{}剩余时间: {}秒", voucherId, ttl);
    }

    return ttl;
}
```

## 📈 优化效果

### 内存使用优化

**修复前**：

```
Redis内存持续增长 ↗️
每个秒杀活动数据永久保存
内存利用率低，存在泄漏风险
```

**修复后**：

```
Redis内存平稳运行 ➡️
活动结束后数据自动清理
内存利用率高，无泄漏风险
```

### 业务逻辑优化

**修复前**：

```
过期活动数据仍可被查询
需要手动清理历史数据
无法通过TTL判断活动状态
```

**修复后**：

```
过期活动数据自动清理 ✅
系统自动管理数据生命周期 ✅
可通过TTL监控活动状态 ✅
```

## 🎯 面试要点

### 1. 技术深度体现

> **面试官**："为什么要设置 Redis 过期时间？"
>
> **回答**："主要考虑三个方面：
>
> 1. **内存管理**：防止 Redis 内存无限增长，避免 OOM
> 2. **业务逻辑**：秒杀活动有时效性，过期数据应该自动清理
> 3. **性能优化**：减少无效数据，提升 Redis 查询性能"

### 2. 架构设计思维

> **面试官**："如何设计不同数据的过期时间？"
>
> **回答**："我采用分层 TTL 策略：
>
> 1. **库存数据 24 小时**：与活动周期对齐
> 2. **用户记录 25 小时**：比库存稍长，确保防重复购买逻辑正确
> 3. **订单状态 30 分钟-2 小时**：根据订单处理时长动态调整
>
> 这样既保证了业务逻辑的正确性，又优化了内存使用。"

### 3. 监控和运维

> **面试官**："如何监控 TTL 设置是否合理？"
>
> **回答**："提供了 TTL 查询接口，可以：
>
> 1. **实时监控**：查看活动剩余时间
> 2. **异常检测**：发现未设置 TTL 的数据(-1 返回值)
> 3. **容量规划**：根据 TTL 预估内存使用量"

## 🚀 技术亮点总结

### 关键技术点

1. **⭐ 分层 TTL 设计**：不同业务数据设置不同过期时间
2. **⭐ 动态 TTL 管理**：支持运行时调整过期时间
3. **⭐ TTL 监控机制**：实时查看数据过期状态
4. **⭐ 自动化清理**：系统自动管理数据生命周期

### 架构优势

- ✅ **内存优化**：有效防止 Redis 内存泄漏
- ✅ **业务对齐**：TTL 与业务生命周期完全匹配
- ✅ **运维友好**：支持监控和动态调整
- ✅ **性能提升**：减少无效数据，提升查询性能

---

**这个 TTL 策略设计展现了你对 Redis 深层原理的理解，以及系统化的架构思维，是面试中的重要加分项！**
