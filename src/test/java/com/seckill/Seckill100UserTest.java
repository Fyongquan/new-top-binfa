package com.seckill;

import com.seckill.dto.SeckillRequest;
import com.seckill.dto.SeckillResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 100用户秒杀并发测试
 * 
 * @author seckill-test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Seckill100UserTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/seckill";
    }

    @Test
    @Order(1)
    @DisplayName("🔥 100用户秒杀测试 - 50库存")
    void test100UsersSeckill50Stock() throws Exception {
        System.out.println("\n🚀 ========== 100用户秒杀测试开始 ==========");
        
        // 测试参数配置
        int userCount = 100;
        int stockCount = 50;
        Long voucherId = 100L;
        
        // 1. 初始化库存
        System.out.println("📦 初始化库存: " + stockCount);
        String initUrl = baseUrl + "/init?voucherId=" + voucherId + "&stock=" + stockCount;
        ResponseEntity<Map> initResponse = restTemplate.postForEntity(initUrl, null, Map.class);
        assertEquals(HttpStatus.OK, initResponse.getStatusCode());
        Thread.sleep(1000); // 确保初始化完成

        // 2. 验证初始库存
        String stockUrl = baseUrl + "/stock/" + voucherId;
        ResponseEntity<Map> stockCheck = restTemplate.getForEntity(stockUrl, Map.class);
        assertEquals(stockCount, ((Number) stockCheck.getBody().get("currentStock")).intValue());
        System.out.println("✅ 库存初始化完成: " + stockCheck.getBody().get("currentStock"));

        // 3. 并发测试设置
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(userCount);
        ExecutorService executor = Executors.newFixedThreadPool(50); // 50个线程池
        
        // 结果统计
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger stockNotEnoughCount = new AtomicInteger(0);
        AtomicInteger limitExceededCount = new AtomicInteger(0);
        AtomicInteger systemErrorCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        System.out.println("👥 启动" + userCount + "个并发用户...");
        
        // 4. 启动并发请求
        for (int i = 0; i < userCount; i++) {
            final long userId = 10000L + i;
            final int userIndex = i;
            
            executor.submit(() -> {
                try {
                    startSignal.await(); // 等待统一开始信号

                    SeckillRequest request = new SeckillRequest();
                    request.setUserId(userId);
                    request.setVoucherId(voucherId);
                    request.setLimit(1);

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<SeckillRequest> entity = new HttpEntity<>(request, headers);

                    long requestStart = System.currentTimeMillis();
                    ResponseEntity<SeckillResponse> response = restTemplate.postForEntity(
                        baseUrl, entity, SeckillResponse.class);
                    long requestEnd = System.currentTimeMillis();

                    if (response.getBody() != null) {
                        SeckillResponse result = response.getBody();
                        switch (result.getCode()) {
                            case 0: // 成功
                                successCount.incrementAndGet();
                                if (successCount.get() <= 10) { // 只打印前10个成功的
                                    System.out.println("🎉 用户" + userId + " 秒杀成功！耗时: " + 
                                        (requestEnd - requestStart) + "ms, 订单: " + result.getOrderId());
                                }
                                break;
                            case 1: // 库存不足
                                stockNotEnoughCount.incrementAndGet();
                                break;
                            case 2: // 超过限购
                                limitExceededCount.incrementAndGet();
                                break;
                            case 3: // 系统异常
                                systemErrorCount.incrementAndGet();
                                if (systemErrorCount.get() <= 3) { // 打印前3个系统错误
                                    System.err.println("💥 用户" + userId + " 系统异常: " + result.getMessage());
                                }
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
                    System.err.println("💥 用户" + userId + " 请求异常: " + e.getMessage());
                } finally {
                    doneSignal.countDown();
                }
            });
        }

        // 5. 开始测试并计时
        System.out.println("🚀 所有用户就位，开始秒杀！");
        long testStartTime = System.currentTimeMillis();
        startSignal.countDown(); // 释放开始信号
        doneSignal.await(); // 等待所有请求完成
        long testEndTime = System.currentTimeMillis();
        
        executor.shutdown();

        // 6. 等待异步消息处理
        System.out.println("⏳ 等待异步订单处理完成...");
        Thread.sleep(5000);

        // 7. 验证最终库存
        ResponseEntity<Map> finalStockResponse = restTemplate.getForEntity(stockUrl, Map.class);
        int finalStock = ((Number) finalStockResponse.getBody().get("currentStock")).intValue();

        // 8. 输出详细测试结果
        System.out.println("\n🏆 ========== 100用户秒杀测试结果 ==========");
        System.out.println("👥 总用户数: " + userCount);
        System.out.println("📦 初始库存: " + stockCount);
        System.out.println("📦 最终库存: " + finalStock);
        System.out.println("⏰ 总耗时: " + (testEndTime - testStartTime) + "ms");
        System.out.println("🚀 平均QPS: " + String.format("%.2f", userCount * 1000.0 / (testEndTime - testStartTime)));
        System.out.println("=======================================");
        System.out.println("✅ 秒杀成功: " + successCount.get() + " (" + 
            String.format("%.1f", successCount.get() * 100.0 / userCount) + "%)");
        System.out.println("📦 库存不足: " + stockNotEnoughCount.get() + " (" + 
            String.format("%.1f", stockNotEnoughCount.get() * 100.0 / userCount) + "%)");
        System.out.println("🚫 超过限购: " + limitExceededCount.get() + " (" + 
            String.format("%.1f", limitExceededCount.get() * 100.0 / userCount) + "%)");
        System.out.println("💥 系统异常: " + systemErrorCount.get() + " (" + 
            String.format("%.1f", systemErrorCount.get() * 100.0 / userCount) + "%)");
        System.out.println("❓ 其他错误: " + otherErrorCount.get() + " (" + 
            String.format("%.1f", otherErrorCount.get() * 100.0 / userCount) + "%)");
        System.out.println("=======================================");

        // 9. 数据一致性验证
        int expectedFinalStock = Math.max(0, stockCount - successCount.get());
        assertEquals(expectedFinalStock, finalStock, "最终库存应该正确");
        assertEquals(stockCount, successCount.get(), "成功数量应该等于库存数量");
        
        // 10. 性能要求验证（可根据需要调整）
        assertTrue(testEndTime - testStartTime < 10000, "100用户并发应该在10秒内完成");
        assertTrue(systemErrorCount.get() < userCount * 0.05, "系统错误率应该低于5%");
        
        System.out.println("🎯 数据一致性验证: ✅ 通过");
        System.out.println("🎯 性能要求验证: ✅ 通过");
        System.out.println("🏆 100用户秒杀测试完美通过！");
    }

    @Test
    @Order(2)
    @DisplayName("🔥 100用户秒杀测试 - 20库存（高竞争）")
    void test100UsersSeckill20Stock() throws Exception {
        System.out.println("\n🚀 ========== 100用户抢20库存高竞争测试 ==========");
        
        // 高竞争测试：100用户抢20库存
        int userCount = 100;
        int stockCount = 20;
        Long voucherId = 200L;
        
        // 初始化库存
        String initUrl = baseUrl + "/init?voucherId=" + voucherId + "&stock=" + stockCount;
        restTemplate.postForEntity(initUrl, null, Map.class);
        Thread.sleep(1000);

        // 并发测试
        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch doneSignal = new CountDownLatch(userCount);
        ExecutorService executor = Executors.newFixedThreadPool(80); // 更高并发
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < userCount; i++) {
            final long userId = 20000L + i;
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

        long startTime = System.currentTimeMillis();
        startSignal.countDown();
        doneSignal.await();
        long endTime = System.currentTimeMillis();
        
        executor.shutdown();
        Thread.sleep(3000);

        // 验证结果
        System.out.println("🏆 高竞争测试结果:");
        System.out.println("👥 总用户: " + userCount + " | 📦 库存: " + stockCount);
        System.out.println("✅ 成功: " + successCount.get() + " | ❌ 失败: " + failureCount.get());
        System.out.println("⏰ 耗时: " + (endTime - startTime) + "ms");
        System.out.println("🎯 竞争率: " + String.format("%.1f:1", (double) userCount / stockCount));

        assertEquals(stockCount, successCount.get(), "高竞争下成功数应该等于库存数");
        assertEquals(userCount - stockCount, failureCount.get(), "失败数应该正确");
        
        System.out.println("🏆 高竞争测试完美通过！");
    }
}
