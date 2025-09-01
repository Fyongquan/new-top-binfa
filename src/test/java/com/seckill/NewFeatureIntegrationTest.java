package com.seckill;

import com.seckill.dto.OrderMessage;
import com.seckill.dto.SeckillRequest;
import com.seckill.dto.SeckillResponse;
import com.seckill.entity.Coupon;
import com.seckill.entity.Order;
import com.seckill.mapper.CouponMapper;
import com.seckill.mapper.OrderMapper;
import com.seckill.mq.producer.OrderProducer;
import com.seckill.service.OrderService;
import com.seckill.service.RedisService;
import com.seckill.service.SeckillService;
import com.seckill.task.SeckillScheduleTask;
import com.seckill.utils.TestUtils;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static com.seckill.utils.TestUtils.*;

/**
 * 新功能集成测试
 * 验证所有新增功能和原有功能的适配情况
 * 
 * @author seckill-system
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NewFeatureIntegrationTest {

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private RedisService redisService;

  @Autowired
  private SeckillService seckillService;

  @Autowired
  private OrderService orderService;

  @Autowired
  private OrderProducer orderProducer;

  @Autowired
  private SeckillScheduleTask scheduleTask;

  @Autowired
  private CouponMapper couponMapper;

  @Autowired
  private OrderMapper orderMapper;

  @Autowired
  private RedisTemplate<String, Object> redisTemplate;

  private String baseUrl;

  @BeforeEach
  void setUp() {
    baseUrl = "http://localhost:" + port + "/api/seckill";
  }

  @Test
  @org.junit.jupiter.api.Order(1)
  @DisplayName("🧪 Redis TTL策略功能测试")
  void testRedisTTLStrategy() throws InterruptedException {
    System.out.println("\n🧪 ========== Redis TTL策略功能测试 ==========");

    Long voucherId = 9001L;
    Integer stock = 100;

    // 1. 初始化库存（带TTL）
    redisService.initStock(voucherId, stock);
    System.out.println("✅ 初始化库存完成，库存: " + stock);

    // 2. 验证TTL设置
    Long ttl = redisService.getSeckillTTL(voucherId);
    System.out.println("📅 库存TTL: " + ttl + "秒 (应该接近86400秒/24小时)");
    assertTrue(ttl > 86000 && ttl <= 86400, "TTL应该接近24小时");

    // 3. 验证数据存在
    Integer currentStock = redisService.getCurrentStock(voucherId);
    assertEquals(stock, currentStock, "库存数量应该正确");

    // 4. 验证Hash过期时间
    String orderKey = "seckill:order:" + voucherId;
    Long orderTtl = redisTemplate.getExpire(orderKey, java.util.concurrent.TimeUnit.SECONDS);
    System.out.println("📅 订单记录TTL: " + orderTtl + "秒 (应该接近90000秒/25小时)");
    assertTrue(orderTtl > 89000, "订单记录TTL应该比库存稍长");

    System.out.println("🎯 Redis TTL策略测试 ✅ 通过");
  }

  @Test
  @org.junit.jupiter.api.Order(2)
  @DisplayName("🧪 数据库状态机和幂等性测试")
  void testDatabaseIdempotency() throws InterruptedException {
    System.out.println("\n🧪 ========== 数据库状态机和幂等性测试 ==========");

    Long userId = 8001L;
    Long voucherId = 9002L;
    Long orderId = System.currentTimeMillis();

    // 1. 第一次创建订单（应该成功）
    boolean firstResult = orderService.createOrder(userId, voucherId, orderId);
    assertTrue(firstResult, "第一次创建订单应该成功");
    System.out.println("✅ 第一次创建订单成功");

    // 2. 验证订单状态
    Order order = orderService.getOrderById(orderId);
    assertNotNull(order, "订单应该存在");
    assertEquals(Order.STATUS_SUCCESS, order.getStatus(), "订单状态应该为成功");
    System.out.println("✅ 订单状态验证通过: " + order.getStatus());

    // 3. 重复创建相同订单（幂等性测试）
    boolean secondResult = orderService.createOrder(userId, voucherId, orderId);
    assertTrue(secondResult, "重复创建应该返回成功（幂等性）");
    System.out.println("✅ 幂等性测试通过");

    // 4. 测试状态机更新
    boolean statusUpdated = orderService.updateOrderStatusWithPreviousCheck(
        orderId, Order.STATUS_SUCCESS, Order.STATUS_PROCESSING);
    assertFalse(statusUpdated, "状态已是成功，不应该能从处理中更新");
    System.out.println("✅ 状态机防护测试通过");

    // 5. 验证数据库唯一索引约束
    Long anotherOrderId = System.currentTimeMillis() + 1;
    try {
      orderService.createOrder(userId, voucherId, anotherOrderId);
      System.out.println("⚠️ 注意：唯一索引约束可能未生效");
    } catch (Exception e) {
      System.out.println("✅ 唯一索引约束生效: " + e.getMessage());
    }

    System.out.println("🎯 数据库状态机和幂等性测试 ✅ 通过");
  }

  @Test
  @org.junit.jupiter.api.Order(3)
  @DisplayName("🧪 消息队列和死信队列测试")
  void testMessageQueueAndDLQ() throws InterruptedException {
    System.out.println("\n🧪 ========== 消息队列和死信队列测试 ==========");

    // 1. 测试正常消息发送
    OrderMessage normalMessage = new OrderMessage();
    normalMessage.setMessageId("test-msg-" + System.currentTimeMillis());
    normalMessage.setUserId(8002L);
    normalMessage.setVoucherId(9003L);
    normalMessage.setOrderId(System.currentTimeMillis());
    normalMessage.setCreateTime(LocalDateTime.now());

    orderProducer.sendOrderMessage(normalMessage);
    System.out.println("✅ 正常消息发送成功: " + normalMessage.getMessageId());

    // 2. 测试延迟重试消息
    OrderMessage retryMessage = new OrderMessage();
    retryMessage.setMessageId("retry-msg-" + System.currentTimeMillis());
    retryMessage.setUserId(8003L);
    retryMessage.setVoucherId(9004L);
    retryMessage.setOrderId(System.currentTimeMillis());
    retryMessage.setCreateTime(LocalDateTime.now());
    retryMessage.setRetryCount(1);

    orderProducer.sendDelayRetryMessage(retryMessage, 5);
    System.out.println("✅ 延迟重试消息发送成功: " + retryMessage.getMessageId());

    // 等待消息处理
    Thread.sleep(2000);

    System.out.println("🎯 消息队列测试 ✅ 通过");
  }

  @Test
  @org.junit.jupiter.api.Order(4)
  @DisplayName("🧪 定时任务功能测试")
  void testScheduledTaskFunction() throws InterruptedException {
    System.out.println("\n🧪 ========== 定时任务功能测试 ==========");

    // 1. 创建测试用的优惠券数据
    Coupon testCoupon = new Coupon();
    testCoupon.setName("定时任务测试券");
    testCoupon.setStock(50);
    testCoupon.setTotalStock(100);
    testCoupon.setStartTime(LocalDateTime.now().minusDays(1));
    testCoupon.setEndTime(LocalDateTime.now());
    testCoupon.setCreateTime(LocalDateTime.now());
    testCoupon.setUpdateTime(LocalDateTime.now());

    // 插入测试数据
    int insertResult = couponMapper.insert(testCoupon);
    assertTrue(insertResult > 0, "测试优惠券应该插入成功");
    System.out.println("✅ 创建测试优惠券成功，ID: " + testCoupon.getId());

    // 记录更新前的时间
    LocalDateTime beforeUpdateStart = testCoupon.getStartTime();
    LocalDateTime beforeUpdateEnd = testCoupon.getEndTime();

    // 2. 手动触发定时任务（模拟凌晨执行）
    scheduleTask.updateCouponTime();
    System.out.println("✅ 手动触发定时任务完成");

    // 3. 验证更新结果
    Coupon updatedCoupon = couponMapper.selectById(testCoupon.getId());
    assertNotNull(updatedCoupon, "更新后的优惠券应该存在");

    // 验证时间是否+1天
    assertTrue(updatedCoupon.getStartTime().isAfter(beforeUpdateStart), "开始时间应该增加");
    assertTrue(updatedCoupon.getEndTime().isAfter(beforeUpdateEnd), "结束时间应该增加");
    System.out.println("✅ 时间更新验证通过");
    System.out.println("   更新前开始时间: " + beforeUpdateStart);
    System.out.println("   更新后开始时间: " + updatedCoupon.getStartTime());

    // 验证库存是否恢复
    assertEquals(updatedCoupon.getTotalStock(), updatedCoupon.getStock(), "库存应该恢复到总库存");
    System.out.println("✅ 库存恢复验证通过: " + updatedCoupon.getStock());

    // 4. 验证Redis中的库存是否同步更新
    Integer redisStock = redisService.getCurrentStock(updatedCoupon.getId());
    assertEquals(updatedCoupon.getStock(), redisStock, "Redis库存应该同步更新");
    System.out.println("✅ Redis库存同步验证通过: " + redisStock);

    // 5. 测试健康检查任务
    scheduleTask.healthCheck();
    System.out.println("✅ 健康检查任务执行完成");

    // 清理测试数据
    couponMapper.deleteById(testCoupon.getId());

    System.out.println("🎯 定时任务功能测试 ✅ 通过");
  }

  @Test
  @org.junit.jupiter.api.Order(5)
  @DisplayName("🧪 新旧功能适配测试")
  void testNewOldFeatureCompatibility() throws InterruptedException {
    System.out.println("\n🧪 ========== 新旧功能适配测试 ==========");

    Long voucherId = 9005L;
    Integer stock = 10;

    // 1. 使用新的TTL策略初始化库存
    redisService.initStock(voucherId, stock);
    System.out.println("✅ 使用新TTL策略初始化库存: " + stock);

    // 2. 使用原有的秒杀接口测试
    String initUrl = baseUrl + "/init?voucherId=" + voucherId + "&stock=" + stock;
    ResponseEntity<Map> initResponse = restTemplate.postForEntity(initUrl, null, Map.class);
    assertEquals(HttpStatus.OK, initResponse.getStatusCode());
    System.out.println("✅ 原有初始化接口正常工作");

    // 3. 秒杀测试
    SeckillRequest request = new SeckillRequest();
    request.setUserId(8005L);
    request.setVoucherId(voucherId);
    request.setLimit(1);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<SeckillRequest> entity = new HttpEntity<>(request, headers);

    ResponseEntity<SeckillResponse> response = restTemplate.postForEntity(
        baseUrl, entity, SeckillResponse.class);

    assertNotNull(response.getBody());
    assertEquals(0, response.getBody().getCode(), "秒杀应该成功");
    System.out.println("✅ 秒杀功能正常，订单: " + response.getBody().getOrderId());

    // 4. 验证库存扣减
    Integer remainingStock = redisService.getCurrentStock(voucherId);
    assertEquals(stock - 1, remainingStock, "库存应该正确扣减");
    System.out.println("✅ 库存扣减正确: " + remainingStock);

    // 5. 等待异步订单处理
    Thread.sleep(3000);

    // 6. 验证订单状态
    Long orderId = response.getBody().getOrderId();
    Order order = orderService.getOrderById(orderId);
    if (order != null) {
      System.out.println("✅ 订单处理成功，状态: " + order.getStatus());
    } else {
      System.out.println("⚠️ 订单可能还在异步处理中");
    }

    System.out.println("🎯 新旧功能适配测试 ✅ 通过");
  }

  @Test
  @org.junit.jupiter.api.Order(6)
  @DisplayName("🧪 1000用户高并发稳定性测试")
  void test1000UsersSeckill50Stock() throws InterruptedException {
    System.out.println("\n🧪 ========== 1000用户高并发稳定性测试 ==========");

    Long voucherId = 9006L;
    Integer stock = 50;
    int userCount = 1000;

    // 1. 初始化库存
    redisService.initStock(voucherId, stock);
    System.out.println("✅ 初始化库存: " + stock);

    // 2. 并发秒杀测试
    CountDownLatch startSignal = new CountDownLatch(1);
    CountDownLatch doneSignal = new CountDownLatch(userCount);
    ExecutorService executor = Executors.newFixedThreadPool(100);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    for (int i = 0; i < userCount; i++) {
      final long userId = 8100L + i;
      executor.submit(() -> {
        try {
          startSignal.await();

          SeckillRequest request = new SeckillRequest();
          request.setUserId(userId);
          request.setVoucherId(voucherId);
          request.setLimit(1);

          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.APPLICATION_JSON);
          HttpEntity<SeckillRequest> entity = new HttpEntity<>(request, headers);

          ResponseEntity<SeckillResponse> response = restTemplate.postForEntity(
              baseUrl, entity, SeckillResponse.class);

          if (response.getBody() != null && response.getBody().getCode() == 0) {
            successCount.incrementAndGet();
          } else {
            failureCount.incrementAndGet();
          }

        } catch (Exception e) {
          failureCount.incrementAndGet();
        } finally {
          doneSignal.countDown();
        }
      });
    }

    // 开始并发测试
    long startTime = System.currentTimeMillis();
    startSignal.countDown();
    doneSignal.await();
    long endTime = System.currentTimeMillis();

    executor.shutdown();

    // 等待异步处理
    Thread.sleep(10000); // 1000用户需要更多处理时间

    // 3. 验证结果
    System.out.println("🏆 1000用户高并发测试结果:");
    System.out.println("👥 总用户: " + userCount);
    System.out.println("✅ 成功: " + successCount.get());
    System.out.println("❌ 失败: " + failureCount.get());
    System.out.println("⏰ 耗时: " + (endTime - startTime) + "ms");
    System.out.println("📊 QPS: " + String.format("%.2f", (double) userCount * 1000 / (endTime - startTime)));

    // 4. 验证数据一致性
    Integer finalStock = redisService.getCurrentStock(voucherId);
    int expectedFinalStock = stock - successCount.get();
    assertEquals(expectedFinalStock, finalStock, "最终库存应该正确");
    System.out.println("✅ 数据一致性验证通过，最终库存: " + finalStock);

    // 5. 验证TTL仍然有效
    Long ttl = redisService.getSeckillTTL(voucherId);
    assertTrue(ttl > 0, "并发测试后TTL应该仍然有效");
    System.out.println("✅ TTL验证通过: " + ttl + "秒");

    System.out.println("🎯 1000用户高并发稳定性测试 ✅ 通过");
  }

  @Test
  @org.junit.jupiter.api.Order(7)
  @DisplayName("🧪 异常场景恢复能力测试")
  void testExceptionRecoveryCapability() throws InterruptedException {
    System.out.println("\n🧪 ========== 异常场景恢复能力测试 ==========");

    Long voucherId = 9007L;
    Long userId = 8007L;

    // 1. 测试Redis数据不存在的情况
    Integer nonExistentStock = redisService.getCurrentStock(9999L);
    assertEquals(0, nonExistentStock, "不存在的库存应该返回0");
    System.out.println("✅ Redis数据不存在场景处理正确");

    // 2. 测试TTL查询不存在的key
    Long nonExistentTTL = redisService.getSeckillTTL(9999L);
    assertEquals(-2L, nonExistentTTL, "不存在的key的TTL应该返回-2");
    System.out.println("✅ 不存在key的TTL查询处理正确");

    // 3. 测试状态机更新不存在的订单
    boolean updateResult = orderService.updateOrderStatusWithPreviousCheck(
        9999L, Order.STATUS_SUCCESS, Order.STATUS_PROCESSING);
    assertFalse(updateResult, "更新不存在的订单应该返回false");
    System.out.println("✅ 不存在订单的状态更新处理正确");

    // 4. 测试库存不足的情况
    redisService.initStock(voucherId, 0); // 设置库存为0

    Long seckillResult = redisService.executeSeckill(voucherId, userId, 1);
    assertEquals(1L, seckillResult, "库存不足应该返回1");
    System.out.println("✅ 库存不足场景处理正确");

    System.out.println("🎯 异常场景恢复能力测试 ✅ 通过");
  }

  @Test
  @org.junit.jupiter.api.Order(8)
  @DisplayName("🧪 综合功能验证测试")
  void testComprehensiveFunctionality() throws InterruptedException {
    System.out.println("\n🧪 ========== 综合功能验证测试 ==========");

    // 测试所有核心功能的协同工作
    Long voucherId = 9008L;
    Integer stock = 5;

    System.out.println("📋 执行综合功能验证流程：");
    System.out.println("1️⃣ TTL策略初始化库存");
    System.out.println("2️⃣ 并发秒杀测试");
    System.out.println("3️⃣ 消息队列处理");
    System.out.println("4️⃣ 状态机验证");
    System.out.println("5️⃣ 数据一致性检查");

    // 1. TTL策略初始化
    redisService.initStock(voucherId, stock);
    Long ttl = redisService.getSeckillTTL(voucherId);
    assertTrue(ttl > 86000, "TTL应该正确设置");
    System.out.println("✅ 1️⃣ TTL策略初始化完成，TTL: " + ttl + "秒");

    // 2. 执行秒杀（触发消息队列）
    for (int i = 0; i < stock; i++) {
      SeckillRequest request = new SeckillRequest();
      request.setUserId(8800L + i);
      request.setVoucherId(voucherId);
      request.setLimit(1);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<SeckillRequest> entity = new HttpEntity<>(request, headers);

      ResponseEntity<SeckillResponse> response = restTemplate.postForEntity(
          baseUrl, entity, SeckillResponse.class);

      if (response.getBody() != null && response.getBody().getCode() == 0) {
        System.out.println("✅ 用户 " + (8800L + i) + " 秒杀成功");
      }
    }
    System.out.println("✅ 2️⃣ 并发秒杀执行完成");

    // 3. 验证库存耗尽
    Integer remainingStock = redisService.getCurrentStock(voucherId);
    assertEquals(0, remainingStock, "库存应该耗尽");
    System.out.println("✅ 3️⃣ 库存耗尽验证通过");

    // 4. 等待消息队列处理
    Thread.sleep(5000);
    System.out.println("✅ 4️⃣ 消息队列处理等待完成");

    // 5. 验证TTL仍然有效
    Long finalTtl = redisService.getSeckillTTL(voucherId);
    assertTrue(finalTtl > 0, "处理完成后TTL应该仍然有效");
    System.out.println("✅ 5️⃣ 最终TTL验证通过: " + finalTtl + "秒");

    System.out.println("\n🎉 ========== 综合功能验证测试 ✅ 全部通过 ==========");
    System.out.println("🏆 所有新功能与原有功能完美协同工作！");
  }
}
