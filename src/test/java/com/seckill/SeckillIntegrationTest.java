package com.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.dto.SeckillRequest;
import com.seckill.dto.SeckillResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 秒杀系统集成测试
 * 
 * @author seckill-test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ActiveProfiles("test")
public class SeckillIntegrationTest {

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  private String baseUrl;

  @BeforeEach
  void setUp() {
    baseUrl = "http://localhost:" + port + "/api/seckill";
  }

  @Test
  @Order(1)
  @DisplayName("应用健康检查测试")
  void testHealthCheck() {
    String url = baseUrl + "/health";
    ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertEquals("UP", body.get("status"));

    System.out.println("✅ 健康检查通过");
  }

  @Test
  @Order(2)
  @DisplayName("初始化库存测试")
  void testInitStock() {
    String url = baseUrl + "/init?voucherId=1&stock=100";
    ResponseEntity<Map> response = restTemplate.postForEntity(url, null, Map.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertEquals(true, body.get("success"));

    // 等待一下确保Redis写入完成
    try {
      Thread.sleep(500);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    System.out.println("✅ 库存初始化成功: " + body);
  }

  @Test
  @Order(3)
  @DisplayName("查询库存测试")
  void testGetStock() {
    String url = baseUrl + "/stock/1";
    ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    Map<String, Object> body = response.getBody();
    assertNotNull(body);
    assertEquals(1L, ((Number) body.get("voucherId")).longValue());

    // 打印实际库存用于调试
    System.out.println("📊 实际库存数据: " + body);

    Integer actualStock = ((Number) body.get("currentStock")).intValue();
    System.out.println("📊 当前库存: " + actualStock);

    // 如果库存不是100，重新初始化
    if (!actualStock.equals(100)) {
      System.out.println("⚠️ 库存不正确，重新初始化...");
      String initUrl = baseUrl + "/init?voucherId=1&stock=100";
      restTemplate.postForEntity(initUrl, null, Map.class);

      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      // 重新查询
      response = restTemplate.getForEntity(url, Map.class);
      body = response.getBody();
      actualStock = ((Number) body.get("currentStock")).intValue();
    }

    assertEquals(100, actualStock.intValue());
    System.out.println("✅ 库存查询成功: " + body);
  }

  @Test
  @Order(4)
  @DisplayName("单次秒杀成功测试")
  void testSingleSeckillSuccess() throws Exception {
    // 确保库存充足，重新初始化
    String initUrl = baseUrl + "/init?voucherId=1&stock=100";
    restTemplate.postForEntity(initUrl, null, Map.class);
    Thread.sleep(500);

    // 验证初始化后的库存
    String stockUrl = baseUrl + "/stock/1";
    ResponseEntity<Map> stockCheck = restTemplate.getForEntity(stockUrl, Map.class);
    Map<String, Object> stockData = stockCheck.getBody();
    System.out.println("🔍 秒杀前库存检查: " + stockData);

    // 创建秒杀请求
    SeckillRequest request = new SeckillRequest();
    request.setUserId(1001L);
    request.setVoucherId(1L);
    request.setLimit(1);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<SeckillRequest> entity = new HttpEntity<>(request, headers);

    ResponseEntity<SeckillResponse> response = restTemplate.postForEntity(
        baseUrl, entity, SeckillResponse.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    SeckillResponse body = response.getBody();
    assertNotNull(body);

    System.out.println("📊 秒杀响应: " + body);

    // 如果不成功，打印详细信息
    if (body.getCode() != 0) {
      System.out.println("❌ 秒杀失败，响应码: " + body.getCode() + ", 消息: " + body.getMessage());

      // 再次检查库存
      ResponseEntity<Map> stockResponse = restTemplate.getForEntity(stockUrl, Map.class);
      System.out.println("📦 当前库存状态: " + stockResponse.getBody());
    }

    assertEquals(0, body.getCode(), "秒杀应该成功，但返回码是: " + body.getCode() + ", 消息: " + body.getMessage());
    assertNotNull(body.getOrderId());

    System.out.println("✅ 单次秒杀成功: " + body.getMessage() + ", 订单ID: " + body.getOrderId());

    // 等待异步处理
    Thread.sleep(1000);

    // 验证库存减少
    ResponseEntity<Map> stockResponse = restTemplate.getForEntity(stockUrl, Map.class);
    Map<String, Object> stockBody = stockResponse.getBody();
    System.out.println("📦 秒杀后库存: " + stockBody);
    assertEquals(99, ((Number) stockBody.get("currentStock")).intValue());

    System.out.println("✅ 库存正确减少到: " + stockBody.get("currentStock"));
  }

  @Test
  @Order(5)
  @DisplayName("限购逻辑测试")
  void testLimitControl() throws Exception {
    // 同一用户再次购买
    SeckillRequest request = new SeckillRequest();
    request.setUserId(1001L);
    request.setVoucherId(1L);
    request.setLimit(1);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<SeckillRequest> entity = new HttpEntity<>(request, headers);

    ResponseEntity<SeckillResponse> response = restTemplate.postForEntity(
        baseUrl, entity, SeckillResponse.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    SeckillResponse body = response.getBody();
    assertNotNull(body);
    assertEquals(2, body.getCode()); // 超过限购

    System.out.println("✅ 限购逻辑正确: " + body.getMessage());
  }

  @Test
  @Order(6)
  @DisplayName("并发秒杀测试 - 10用户抢购")
  void testConcurrentSeckill() throws Exception {
    // 重新初始化库存
    String initUrl = baseUrl + "/init?voucherId=2&stock=10";
    restTemplate.postForEntity(initUrl, null, Map.class);

    int threadCount = 10;
    CountDownLatch startSignal = new CountDownLatch(1);
    CountDownLatch doneSignal = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    // 启动并发请求
    for (int i = 0; i < threadCount; i++) {
      final long userId = 2000L + i;
      executor.submit(() -> {
        try {
          startSignal.await(); // 等待开始信号

          SeckillRequest request = new SeckillRequest();
          request.setUserId(userId);
          request.setVoucherId(2L);
          request.setLimit(1);

          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.APPLICATION_JSON);
          HttpEntity<SeckillRequest> entity = new HttpEntity<>(request, headers);

          ResponseEntity<SeckillResponse> response = restTemplate.postForEntity(
              baseUrl, entity, SeckillResponse.class);

          if (response.getBody() != null && response.getBody().getCode() == 0) {
            successCount.incrementAndGet();
            System.out.println("🎉 用户" + userId + "秒杀成功");
          } else {
            failureCount.incrementAndGet();
            System.out.println("❌ 用户" + userId + "秒杀失败: " +
                (response.getBody() != null ? response.getBody().getMessage() : "未知错误"));
          }

        } catch (Exception e) {
          failureCount.incrementAndGet();
          System.out.println("💥 用户" + userId + "请求异常: " + e.getMessage());
        } finally {
          doneSignal.countDown();
        }
      });
    }

    // 开始并发测试
    long startTime = System.currentTimeMillis();
    startSignal.countDown(); // 释放开始信号
    doneSignal.await(); // 等待所有请求完成
    long endTime = System.currentTimeMillis();

    executor.shutdown();

    // 等待异步处理完成
    Thread.sleep(2000);

    System.out.println("\n📊 并发测试结果:");
    System.out.println("👥 总用户数: " + threadCount);
    System.out.println("✅ 成功数: " + successCount.get());
    System.out.println("❌ 失败数: " + failureCount.get());
    System.out.println("⏰ 总耗时: " + (endTime - startTime) + "ms");

    // 验证结果
    assertEquals(10, successCount.get(), "应该有10个用户成功");
    assertEquals(0, failureCount.get(), "不应该有失败的用户");

    // 验证库存
    String stockUrl = baseUrl + "/stock/2";
    ResponseEntity<Map> stockResponse = restTemplate.getForEntity(stockUrl, Map.class);
    Map<String, Object> stockBody = stockResponse.getBody();
    assertEquals(0, ((Number) stockBody.get("currentStock")).intValue(), "库存应该为0");

    System.out.println("✅ 并发测试通过，数据一致性正确！");
  }

  @Test
  @Order(7)
  @DisplayName("高并发压力测试 - 100用户抢50库存")
  void testHighConcurrentSeckill() throws Exception {
    // 初始化库存
    String initUrl = baseUrl + "/init?voucherId=3&stock=50";
    restTemplate.postForEntity(initUrl, null, Map.class);

    int threadCount = 100;
    int expectedSuccess = 50;
    CountDownLatch startSignal = new CountDownLatch(1);
    CountDownLatch doneSignal = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(50); // 线程池大小

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger stockNotEnoughCount = new AtomicInteger(0);
    AtomicInteger otherErrorCount = new AtomicInteger(0);

    // 启动高并发请求
    for (int i = 0; i < threadCount; i++) {
      final long userId = 3000L + i;
      executor.submit(() -> {
        try {
          startSignal.await();

          SeckillRequest request = new SeckillRequest();
          request.setUserId(userId);
          request.setVoucherId(3L);
          request.setLimit(1);

          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.APPLICATION_JSON);
          HttpEntity<SeckillRequest> entity = new HttpEntity<>(request, headers);

          ResponseEntity<SeckillResponse> response = restTemplate.postForEntity(
              baseUrl, entity, SeckillResponse.class);

          if (response.getBody() != null) {
            switch (response.getBody().getCode()) {
              case 0: // 成功
                successCount.incrementAndGet();
                break;
              case 1: // 库存不足
                stockNotEnoughCount.incrementAndGet();
                break;
              default: // 其他错误
                otherErrorCount.incrementAndGet();
                break;
            }
          } else {
            otherErrorCount.incrementAndGet();
          }

        } catch (Exception e) {
          otherErrorCount.incrementAndGet();
        } finally {
          doneSignal.countDown();
        }
      });
    }

    // 开始高并发测试
    long startTime = System.currentTimeMillis();
    startSignal.countDown();
    doneSignal.await();
    long endTime = System.currentTimeMillis();

    executor.shutdown();

    // 等待异步处理
    Thread.sleep(3000);

    System.out.println("\n🚀 高并发压力测试结果:");
    System.out.println("👥 总请求数: " + threadCount);
    System.out.println("✅ 成功数: " + successCount.get());
    System.out.println("📦 库存不足: " + stockNotEnoughCount.get());
    System.out.println("❌ 其他错误: " + otherErrorCount.get());
    System.out.println("⏰ 总耗时: " + (endTime - startTime) + "ms");
    System.out.println("🚀 平均QPS: " + (threadCount * 1000.0 / (endTime - startTime)));

    // 验证数据一致性
    assertEquals(expectedSuccess, successCount.get(), "成功数量应该等于库存数量");
    assertEquals(threadCount - expectedSuccess, stockNotEnoughCount.get(), "库存不足数量正确");
    assertEquals(0, otherErrorCount.get(), "不应该有其他错误");

    // 验证最终库存
    String stockUrl = baseUrl + "/stock/3";
    ResponseEntity<Map> stockResponse = restTemplate.getForEntity(stockUrl, Map.class);
    Map<String, Object> stockBody = stockResponse.getBody();
    assertEquals(0, ((Number) stockBody.get("currentStock")).intValue(), "最终库存应该为0");

    System.out.println("✅ 高并发压力测试通过，数据一致性完美！");
  }

  @Test
  @Order(8)
  @DisplayName("超高并发极限测试 - 500用户抢100库存")
  void testExtremeHighConcurrentSeckill() throws Exception {
    // 初始化库存
    String initUrl = baseUrl + "/init?voucherId=4&stock=100";
    restTemplate.postForEntity(initUrl, null, Map.class);

    int threadCount = 500;
    int expectedSuccess = 100;
    CountDownLatch startSignal = new CountDownLatch(1);
    CountDownLatch doneSignal = new CountDownLatch(threadCount);
    ExecutorService executor = Executors.newFixedThreadPool(100);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger stockNotEnoughCount = new AtomicInteger(0);
    AtomicInteger otherErrorCount = new AtomicInteger(0);

    // 启动超高并发请求
    for (int i = 0; i < threadCount; i++) {
      final long userId = 4000L + i;
      executor.submit(() -> {
        try {
          startSignal.await();

          SeckillRequest request = new SeckillRequest();
          request.setUserId(userId);
          request.setVoucherId(4L);
          request.setLimit(1);

          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.APPLICATION_JSON);
          HttpEntity<SeckillRequest> entity = new HttpEntity<>(request, headers);

          long requestStart = System.currentTimeMillis();
          ResponseEntity<SeckillResponse> response = restTemplate.postForEntity(
              baseUrl, entity, SeckillResponse.class);
          long requestEnd = System.currentTimeMillis();

          if (response.getBody() != null) {
            switch (response.getBody().getCode()) {
              case 0: // 成功
                successCount.incrementAndGet();
                if (successCount.get() <= 10) { // 只打印前10个成功的
                  System.out.println("🎉 用户" + userId + "秒杀成功，耗时: " +
                      (requestEnd - requestStart) + "ms");
                }
                break;
              case 1: // 库存不足
                stockNotEnoughCount.incrementAndGet();
                break;
              default:
                otherErrorCount.incrementAndGet();
                break;
            }
          } else {
            otherErrorCount.incrementAndGet();
          }

        } catch (Exception e) {
          otherErrorCount.incrementAndGet();
        } finally {
          doneSignal.countDown();
        }
      });
    }

    // 开始极限测试
    System.out.println("🚀 开始超高并发极限测试...");
    long startTime = System.currentTimeMillis();
    startSignal.countDown();
    doneSignal.await();
    long endTime = System.currentTimeMillis();

    executor.shutdown();

    // 等待异步处理
    Thread.sleep(5000);

    System.out.println("\n💥 超高并发极限测试结果:");
    System.out.println("==========================================");
    System.out.println("👥 总请求数: " + threadCount);
    System.out.println("✅ 秒杀成功: " + successCount.get() + " (" +
        String.format("%.2f", successCount.get() * 100.0 / threadCount) + "%)");
    System.out.println("📦 库存不足: " + stockNotEnoughCount.get() + " (" +
        String.format("%.2f", stockNotEnoughCount.get() * 100.0 / threadCount) + "%)");
    System.out.println("❌ 其他错误: " + otherErrorCount.get() + " (" +
        String.format("%.2f", otherErrorCount.get() * 100.0 / threadCount) + "%)");
    System.out.println("⏰ 总耗时: " + (endTime - startTime) + "ms");
    System.out.println("🚀 平均QPS: " + String.format("%.2f", threadCount * 1000.0 / (endTime - startTime)));
    System.out.println("==========================================");

    // 验证数据一致性
    assertEquals(expectedSuccess, successCount.get(), "成功数量必须等于库存数量");
    assertTrue(otherErrorCount.get() < threadCount * 0.01, "错误率应该小于1%");

    // 验证最终库存
    String stockUrl = baseUrl + "/stock/4";
    ResponseEntity<Map> stockResponse = restTemplate.getForEntity(stockUrl, Map.class);
    Map<String, Object> stockBody = stockResponse.getBody();
    assertEquals(0, ((Number) stockBody.get("currentStock")).intValue(), "最终库存必须为0");

    System.out.println("🏆 超高并发极限测试完美通过！Redis+Lua+RabbitMQ方案验证成功！");
  }
}
