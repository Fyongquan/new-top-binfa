package com.seckill.debug;

import com.seckill.service.RedisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * 调试测试类 - 用于定位问题
 * 
 * @author seckill-test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DebugTest {

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private RedisService redisService;

  @Test
  void debugSeckillFlow() throws Exception {
    String baseUrl = "http://localhost:" + port + "/api/seckill";

    System.out.println("🔍 开始调试秒杀流程...");
    System.out.println("🌐 测试端口: " + port);
    System.out.println("📡 基础URL: " + baseUrl);

    // 1. 检查健康状态
    System.out.println("\n=== 1. 健康检查 ===");
    try {
      String healthUrl = baseUrl + "/health";
      ResponseEntity<Map> healthResponse = restTemplate.getForEntity(healthUrl, Map.class);
      System.out.println("健康检查状态码: " + healthResponse.getStatusCode());
      System.out.println("健康检查响应: " + healthResponse.getBody());
    } catch (Exception e) {
      System.out.println("❌ 健康检查失败: " + e.getMessage());
    }

    // 2. 查看初始库存状态
    System.out.println("\n=== 2. 查看初始库存状态 ===");
    try {
      String stockUrl = baseUrl + "/stock/1";
      ResponseEntity<Map> stockResponse = restTemplate.getForEntity(stockUrl, Map.class);
      System.out.println("库存查询状态码: " + stockResponse.getStatusCode());
      System.out.println("库存查询响应: " + stockResponse.getBody());
    } catch (Exception e) {
      System.out.println("❌ 库存查询失败: " + e.getMessage());
    }

    // 3. 尝试初始化库存
    System.out.println("\n=== 3. 初始化库存 ===");
    try {
      String initUrl = baseUrl + "/init?voucherId=1&stock=100";
      ResponseEntity<Map> initResponse = restTemplate.postForEntity(initUrl, null, Map.class);
      System.out.println("初始化状态码: " + initResponse.getStatusCode());
      System.out.println("初始化响应: " + initResponse.getBody());
    } catch (Exception e) {
      System.out.println("❌ 初始化失败: " + e.getMessage());
    }

    // 4. 等待一下，再次查看库存
    System.out.println("\n=== 4. 初始化后库存检查 ===");
    Thread.sleep(1000);
    try {
      String stockUrl = baseUrl + "/stock/1";
      ResponseEntity<Map> stockResponse = restTemplate.getForEntity(stockUrl, Map.class);
      System.out.println("库存查询状态码: " + stockResponse.getStatusCode());
      System.out.println("库存查询响应: " + stockResponse.getBody());

      // 先测试KEYS参数传递
      System.out.println("\n--- KEYS参数测试 ---");
      String keysTestResult = redisService.testKeysParameter();
      System.out.println("KEYS参数测试结果: " + keysTestResult);

      // 调用Redis调试方法
      System.out.println("\n--- Redis调试信息 ---");
      redisService.debugRedisData(1L);
    } catch (Exception e) {
      System.out.println("❌ 库存查询失败: " + e.getMessage());
    }

    // 5a. 先执行调试版本的秒杀脚本
    System.out.println("\n=== 5a. 执行调试版秒杀脚本 ===");
    try {
      Long debugResult = redisService.executeSeckillDebug(1L, 1001L, 1);
      System.out.println("调试秒杀脚本结果: " + debugResult);
    } catch (Exception e) {
      System.out.println("❌ 调试秒杀脚本失败: " + e.getMessage());
      e.printStackTrace();
    }

    // 5b. 执行原始的HTTP秒杀请求
    System.out.println("\n=== 5b. 执行HTTP秒杀请求 ===");
    try {
      String seckillUrl = baseUrl;
      String requestBody = "{\"userId\":1002,\"voucherId\":1,\"limit\":1}";

      System.out.println("请求URL: " + seckillUrl);
      System.out.println("请求体: " + requestBody);

      org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
      headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
      org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(requestBody,
          headers);

      ResponseEntity<Map> seckillResponse = restTemplate.postForEntity(seckillUrl, entity, Map.class);
      System.out.println("秒杀状态码: " + seckillResponse.getStatusCode());
      System.out.println("秒杀响应: " + seckillResponse.getBody());

      // 查看Lua脚本的调试信息
      System.out.println("\n--- 秒杀后Redis调试信息 ---");
      redisService.debugRedisData(1L);
    } catch (Exception e) {
      System.out.println("❌ 秒杀请求失败: " + e.getMessage());
      e.printStackTrace();
    }

    // 6. 最终库存检查
    System.out.println("\n=== 6. 最终库存检查 ===");
    Thread.sleep(1000);
    try {
      String stockUrl = baseUrl + "/stock/1";
      ResponseEntity<Map> stockResponse = restTemplate.getForEntity(stockUrl, Map.class);
      System.out.println("最终库存状态码: " + stockResponse.getStatusCode());
      System.out.println("最终库存响应: " + stockResponse.getBody());
    } catch (Exception e) {
      System.out.println("❌ 最终库存查询失败: " + e.getMessage());
    }

    System.out.println("\n🏁 调试流程完成");
  }
}
