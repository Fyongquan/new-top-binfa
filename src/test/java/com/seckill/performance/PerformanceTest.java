package com.seckill.performance;

import com.seckill.dto.SeckillRequest;
import com.seckill.dto.SeckillResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能压力测试类
 * 
 * @author seckill-test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PerformanceTest {

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  @Test
  void performanceStressTest() throws Exception {
    String baseUrl = "http://localhost:" + port + "/api/seckill";

    // 测试场景配置
    TestScenario[] scenarios = {
        new TestScenario(100, 20, 5), // 100用户抢20库存，5个线程池
        new TestScenario(500, 50, 10), // 500用户抢50库存，10个线程池
        new TestScenario(1000, 100, 20), // 1000用户抢100库存，20个线程池
        new TestScenario(2000, 150, 50) // 2000用户抢150库存，50个线程池
    };

    System.out.println("🚀 开始性能压力测试...\n");

    for (int i = 0; i < scenarios.length; i++) {
      TestScenario scenario = scenarios[i];
      System.out.println("📊 测试场景 " + (i + 1) + ": " + scenario.users + "用户抢" +
          scenario.stock + "库存");
      executeScenario(baseUrl, scenario, 10L + i);
      Thread.sleep(3000); // 场景间休息
    }
  }

  private void executeScenario(String baseUrl, TestScenario scenario, Long voucherId) throws Exception {
    // 初始化库存
    String initUrl = baseUrl + "/init?voucherId=" + voucherId + "&stock=" + scenario.stock;
    restTemplate.postForEntity(initUrl, null, Map.class);

    CountDownLatch startSignal = new CountDownLatch(1);
    CountDownLatch doneSignal = new CountDownLatch(scenario.users);
    ExecutorService executor = Executors.newFixedThreadPool(scenario.threadPoolSize);

    // 统计数据
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger stockNotEnoughCount = new AtomicInteger(0);
    AtomicInteger errorCount = new AtomicInteger(0);
    AtomicLong totalResponseTime = new AtomicLong(0);
    AtomicLong minResponseTime = new AtomicLong(Long.MAX_VALUE);
    AtomicLong maxResponseTime = new AtomicLong(0);

    // 启动用户请求
    for (int i = 0; i < scenario.users; i++) {
      final long userId = voucherId * 1000 + i;
      executor.submit(() -> {
        try {
          startSignal.await();

          long requestStart = System.currentTimeMillis();

          SeckillRequest request = new SeckillRequest();
          request.setUserId(userId);
          request.setVoucherId(voucherId);
          request.setLimit(1);

          HttpHeaders headers = new HttpHeaders();
          headers.setContentType(MediaType.APPLICATION_JSON);
          HttpEntity<SeckillRequest> entity = new HttpEntity<>(request, headers);

          ResponseEntity<SeckillResponse> response = restTemplate.postForEntity(
              baseUrl, entity, SeckillResponse.class);

          long requestEnd = System.currentTimeMillis();
          long responseTime = requestEnd - requestStart;

          // 更新响应时间统计
          totalResponseTime.addAndGet(responseTime);
          minResponseTime.updateAndGet(current -> Math.min(current, responseTime));
          maxResponseTime.updateAndGet(current -> Math.max(current, responseTime));

          if (response.getBody() != null) {
            switch (response.getBody().getCode()) {
              case 0:
                successCount.incrementAndGet();
                break;
              case 1:
                stockNotEnoughCount.incrementAndGet();
                break;
              default:
                errorCount.incrementAndGet();
                break;
            }
          } else {
            errorCount.incrementAndGet();
          }

        } catch (Exception e) {
          errorCount.incrementAndGet();
        } finally {
          doneSignal.countDown();
        }
      });
    }

    // 执行测试
    long startTime = System.currentTimeMillis();
    startSignal.countDown();
    doneSignal.await();
    long endTime = System.currentTimeMillis();

    executor.shutdown();

    // 等待异步处理
    Thread.sleep(2000);

    // 计算统计数据
    long totalTime = endTime - startTime;
    double avgResponseTime = totalResponseTime.get() / (double) scenario.users;
    double qps = scenario.users * 1000.0 / totalTime;

    // 验证最终库存
    String stockUrl = baseUrl + "/stock/" + voucherId;
    ResponseEntity<Map> stockResponse = restTemplate.getForEntity(stockUrl, Map.class);
    Map<String, Object> stockBody = stockResponse.getBody();
    int finalStock = ((Number) stockBody.get("currentStock")).intValue();

    // 打印结果
    System.out.println("┌─────────────────────────────────────────────────────────┐");
    System.out.println("│                     测试结果                            │");
    System.out.println("├─────────────────────────────────────────────────────────┤");
    System.out.println("│ 👥 总请求数:      " + String.format("%6d", scenario.users) + "                          │");
    System.out.println("│ ✅ 秒杀成功:      " + String.format("%6d", successCount.get()) + " (" +
        String.format("%5.2f", successCount.get() * 100.0 / scenario.users) + "%)                │");
    System.out.println("│ 📦 库存不足:      " + String.format("%6d", stockNotEnoughCount.get()) + " (" +
        String.format("%5.2f", stockNotEnoughCount.get() * 100.0 / scenario.users) + "%)                │");
    System.out.println("│ ❌ 错误数量:      " + String.format("%6d", errorCount.get()) + " (" +
        String.format("%5.2f", errorCount.get() * 100.0 / scenario.users) + "%)                │");
    System.out.println("│ ⏰ 总耗时:        " + String.format("%6d", totalTime) + " ms                      │");
    System.out.println("│ 🚀 平均QPS:       " + String.format("%6.2f", qps) + "                          │");
    System.out.println("│ 📊 平均响应时间:  " + String.format("%6.2f", avgResponseTime) + " ms                      │");
    System.out.println("│ 📊 最小响应时间:  " + String.format("%6d", minResponseTime.get()) + " ms                      │");
    System.out.println("│ 📊 最大响应时间:  " + String.format("%6d", maxResponseTime.get()) + " ms                      │");
    System.out.println("│ 📦 最终库存:      " + String.format("%6d", finalStock) + "                          │");
    System.out.println("│ 🎯 数据一致性:    " + (successCount.get() == (scenario.stock - finalStock) ? "✅ 通过" : "❌ 失败")
        + "                    │");
    System.out.println("└─────────────────────────────────────────────────────────┘\n");
  }

  static class TestScenario {
    final int users;
    final int stock;
    final int threadPoolSize;

    TestScenario(int users, int stock, int threadPoolSize) {
      this.users = users;
      this.stock = stock;
      this.threadPoolSize = threadPoolSize;
    }
  }
}
