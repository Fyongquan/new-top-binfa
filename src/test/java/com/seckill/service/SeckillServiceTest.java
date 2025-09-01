package com.seckill.service;

import com.seckill.dto.SeckillRequest;
import com.seckill.dto.SeckillResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 秒杀服务单元测试
 * 
 * @author seckill-test
 */
@SpringBootTest
@ActiveProfiles("test")
public class SeckillServiceTest {

  @Autowired
  private SeckillService seckillService;

  @Autowired
  private RedisService redisService;

  @BeforeEach
  void setUp() {
    // 清理测试数据
    try {
      redisService.cleanExpiredSeckillData(999L);
    } catch (Exception e) {
      // 忽略清理错误
    }
  }

  @Test
  @DisplayName("初始化库存测试")
  void testInitStock() {
    Long voucherId = 999L;
    Integer stock = 50;

    seckillService.initSeckillActivity(voucherId, stock);

    Integer currentStock = seckillService.getCurrentStock(voucherId);
    assertEquals(stock, currentStock);
  }

  @Test
  @DisplayName("正常秒杀测试")
  void testNormalSeckill() {
    Long voucherId = 999L;
    Long userId = 1001L;

    // 初始化库存
    seckillService.initSeckillActivity(voucherId, 10);

    // 创建秒杀请求
    SeckillRequest request = new SeckillRequest();
    request.setUserId(userId);
    request.setVoucherId(voucherId);
    request.setLimit(1);

    // 执行秒杀
    SeckillResponse response = seckillService.doSeckill(request);

    // 验证结果
    assertNotNull(response);
    assertEquals(0, response.getCode());
    assertNotNull(response.getOrderId());
    assertTrue(response.getMessage().contains("秒杀成功"));

    // 验证库存减少
    Integer currentStock = seckillService.getCurrentStock(voucherId);
    assertEquals(9, currentStock);

    // 验证用户购买记录
    Integer boughtCount = seckillService.getUserBoughtCount(voucherId, userId);
    assertEquals(1, boughtCount);
  }

  @Test
  @DisplayName("超过限购测试")
  void testExceedLimit() {
    Long voucherId = 999L;
    Long userId = 1002L;

    // 初始化库存
    seckillService.initSeckillActivity(voucherId, 10);

    SeckillRequest request = new SeckillRequest();
    request.setUserId(userId);
    request.setVoucherId(voucherId);
    request.setLimit(1);

    // 第一次购买
    SeckillResponse response1 = seckillService.doSeckill(request);
    assertEquals(0, response1.getCode());

    // 第二次购买（应该被拒绝）
    SeckillResponse response2 = seckillService.doSeckill(request);
    assertEquals(2, response2.getCode());
    assertTrue(response2.getMessage().contains("超过个人限购"));
  }

  @Test
  @DisplayName("库存不足测试")
  void testStockNotEnough() {
    Long voucherId = 999L;

    // 初始化0库存
    seckillService.initSeckillActivity(voucherId, 0);

    SeckillRequest request = new SeckillRequest();
    request.setUserId(1003L);
    request.setVoucherId(voucherId);
    request.setLimit(1);

    // 执行秒杀
    SeckillResponse response = seckillService.doSeckill(request);

    // 验证结果
    assertEquals(1, response.getCode());
    assertTrue(response.getMessage().contains("库存不足"));
  }

  @Test
  @DisplayName("Redis Lua脚本原子性测试")
  void testLuaScriptAtomicity() {
    Long voucherId = 999L;
    Long userId1 = 2001L;
    Long userId2 = 2002L;

    // 初始化1个库存
    seckillService.initSeckillActivity(voucherId, 1);

    SeckillRequest request1 = new SeckillRequest();
    request1.setUserId(userId1);
    request1.setVoucherId(voucherId);
    request1.setLimit(1);

    SeckillRequest request2 = new SeckillRequest();
    request2.setUserId(userId2);
    request2.setVoucherId(voucherId);
    request2.setLimit(1);

    // 执行两次秒杀
    SeckillResponse response1 = seckillService.doSeckill(request1);
    SeckillResponse response2 = seckillService.doSeckill(request2);

    // 验证只有一个成功
    int successCount = 0;
    if (response1.getCode() == 0)
      successCount++;
    if (response2.getCode() == 0)
      successCount++;

    assertEquals(1, successCount, "只能有一个用户成功");

    // 验证最终库存为0
    Integer finalStock = seckillService.getCurrentStock(voucherId);
    assertEquals(0, finalStock);
  }

  @Test
  @DisplayName("库存回滚测试")
  void testStockRollback() {
    Long voucherId = 999L;
    Long userId = 3001L;
    Long orderId = 123456L;

    // 初始化库存
    seckillService.initSeckillActivity(voucherId, 5);

    // 先进行一次成功的秒杀
    SeckillRequest request = new SeckillRequest();
    request.setUserId(userId);
    request.setVoucherId(voucherId);
    request.setLimit(1);

    SeckillResponse response = seckillService.doSeckill(request);
    assertEquals(0, response.getCode());

    // 库存应该减少到4
    assertEquals(4, seckillService.getCurrentStock(voucherId));

    // 执行库存回滚
    seckillService.rollbackStock(voucherId, userId, orderId);

    // 库存应该恢复到5
    assertEquals(5, seckillService.getCurrentStock(voucherId));

    // 用户购买记录应该清零
    assertEquals(0, seckillService.getUserBoughtCount(voucherId, userId));
  }
}
