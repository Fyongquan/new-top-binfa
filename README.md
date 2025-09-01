# 🚀 高并发秒杀系统测试项目

基于 Redis+Lua 脚本+RabbitMQ 实现的高并发秒杀系统，专门用于测试和验证高并发处理能力。

## 🎯 项目特点

- **原子性保证**: 使用 Redis Lua 脚本确保库存扣减的原子性
- **高并发处理**: 基于 Redis 缓存层面预处理，减少数据库压力
- **异步处理**: RabbitMQ 异步处理订单落库，提升用户体验
- **数据一致性**: 完善的回滚机制和幂等性处理
- **监控完善**: 详细的日志记录和性能监控

## 🏗️ 系统架构

```
用户请求 -> 控制器 -> 秒杀服务 -> Redis Lua脚本
                                    ↓
              订单消息 <- RabbitMQ <- 成功响应
                ↓
         订单消费者 -> 数据库落库 -> 更新订单状态
```

## 🛠️ 技术栈

- **后端框架**: Spring Boot 3.4
- **数据库**: MySQL 8.0 + MyBatis
- **缓存**: Redis 7.2
- **消息队列**: RabbitMQ 3.12
- **连接池**: HikariCP
- **JSON 处理**: Jackson
- **容器化**: Docker + Docker Compose

## 📦 快速开始

### 1. 启动依赖服务

```bash
# 启动 Redis、MySQL、RabbitMQ
./scripts/docker-setup.sh

# 或者手动启动
docker-compose up -d
```

### 2. 启动应用

```bash
# Maven 启动
mvn spring-boot:run

# 或者 IDE 启动 SeckillApplication.java
```

### 3. 初始化测试数据

```bash
# 初始化优惠券库存
curl -X POST "http://localhost:8080/api/seckill/init?voucherId=1&stock=100"
```

### 4. 执行并发测试

```bash
# 安装Python依赖
pip install requests

# 运行并发测试（1000用户抢购100个库存）
python scripts/concurrent-test.py --users 1000 --voucher-id 1 --limit 1
```

## 🔧 API 接口

### 秒杀接口

```http
POST /api/seckill
Content-Type: application/json

{
  "userId": 1001,
  "voucherId": 1,
  "limit": 1
}
```

### 查询订单状态

```http
GET /api/seckill/status/{orderId}
```

### 查询库存

```http
GET /api/seckill/stock/{voucherId}
```

### 查询用户购买数量

```http
GET /api/seckill/bought?voucherId=1&userId=1001
```

## 📊 测试结果示例

```
📊 测试结果统计
============================================================
📝 总请求数：1000
✅ 秒杀成功：100 (10.00%)
📦 库存不足：900 (90.00%)
🚫 超过限购：0 (0.00%)
❌ HTTP错误：0 (0.00%)

⏱️ 响应时间统计（毫秒）：
  平均响应时间：45.23
  最小响应时间：12.45
  最大响应时间：156.78
  中位数响应时间：38.90

✅ 数据一致性检查通过
```

## 🎛️ 关键配置

### Redis Lua 脚本

- `seckill.lua`: 秒杀核心逻辑，包含库存检查、限购验证、原子扣减
- `recover_stock.lua`: 库存回滚逻辑，处理订单失败场景

### RabbitMQ 队列

- `seckill.order.queue`: 订单处理主队列
- `seckill.order.delay.queue`: 延迟重试队列
- `seckill.order.dlx.queue`: 死信队列

### 数据库表

- `coupons`: 优惠券信息表
- `orders`: 订单信息表（包含唯一索引防重复）

## 🔍 监控端点

- 应用健康检查: http://localhost:8080/api/seckill/health
- RabbitMQ 管理界面: http://localhost:15672 (guest/guest)
- Redis 管理界面: http://localhost:8081

## 🚨 性能调优建议

1. **Redis 连接池**: 根据并发量调整 Lettuce 连接池大小
2. **数据库连接池**: HikariCP 最大连接数建议设置为 CPU 核数\*2
3. **JVM 参数**: 根据内存情况调整堆内存和 GC 策略
4. **消息队列**: 根据消息量调整 prefetch 和消费者数量

## 🐛 故障排查

### 常见问题

1. **Redis 连接失败**: 检查 Redis 服务状态和连接配置
2. **数据库连接超时**: 检查 MySQL 服务和连接池配置
3. **消息积压**: 检查 RabbitMQ 消费者状态和死信队列

### 日志查看

```bash
# 查看应用日志
tail -f logs/seckill.log

# 查看Docker服务日志
docker-compose logs -f [mysql|redis|rabbitmq]
```

## 📈 扩展建议

1. **分布式锁**: 使用 Redisson 实现分布式锁
2. **限流策略**: 接入 Redis+Lua 实现令牌桶限流
3. **缓存预热**: 定时任务预热热点数据到 Redis
4. **读写分离**: 配置 MySQL 主从复制，读写分离
5. **集群部署**: 使用 Redis Cluster 和 RabbitMQ 集群

---

> 💡 **注意**: 这是一个测试项目，专注于验证高并发处理逻辑，生产环境使用需要补充安全校验、监控告警等功能。
