package com.seckill.controller;

import com.seckill.dto.SeckillRequest;
import com.seckill.dto.SeckillResponse;
import com.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 秒杀控制器
 * 
 * @author seckill-test
 */
@Slf4j
@RestController
@RequestMapping("/api/seckill")
@Validated
public class SeckillController {

  @Resource
  private SeckillService seckillService;

  /**
   * 秒杀接口
   * 
   * @param request 秒杀请求
   * @return 秒杀响应
   */
  @PostMapping("")
  public SeckillResponse seckill(@Valid @RequestBody SeckillRequest request) {
    log.info("收到秒杀请求 - 用户: {}, 优惠券: {}, 限购: {}",
        request.getUserId(), request.getVoucherId(), request.getLimit());

    long startTime = System.currentTimeMillis();
    SeckillResponse response = seckillService.doSeckill(request);
    long endTime = System.currentTimeMillis();

    log.info("秒杀请求处理完成 - 用户: {}, 优惠券: {}, 结果: {}, 耗时: {}ms",
        request.getUserId(), request.getVoucherId(), response.getCode(), (endTime - startTime));

    return response;
  }

  /**
   * 查询订单状态
   * 
   * @param orderId 订单ID
   * @return 订单状态信息
   */
  @GetMapping("/status/{orderId}")
  public Map<String, Object> getOrderStatus(@PathVariable Long orderId) {
    log.info("查询订单状态 - 订单: {}", orderId);

    Integer status = seckillService.getOrderStatus(orderId);

    Map<String, Object> result = new HashMap<>();
    result.put("orderId", orderId);
    result.put("status", status);
    result.put("statusText", getStatusText(status));
    result.put("timestamp", System.currentTimeMillis());

    return result;
  }

  /**
   * 初始化秒杀活动
   * 
   * @param voucherId 优惠券ID
   * @param stock     库存数量
   * @return 初始化结果
   */
  @PostMapping("/init")
  public Map<String, Object> initSeckillActivity(@RequestParam Long voucherId,
      @RequestParam Integer stock) {
    log.info("初始化秒杀活动 - 优惠券: {}, 库存: {}", voucherId, stock);

    try {
      seckillService.initSeckillActivity(voucherId, stock);

      Map<String, Object> result = new HashMap<>();
      result.put("success", true);
      result.put("message", "秒杀活动初始化成功");
      result.put("voucherId", voucherId);
      result.put("stock", stock);
      result.put("timestamp", System.currentTimeMillis());

      return result;
    } catch (Exception e) {
      log.error("初始化秒杀活动失败 - 优惠券: {}, 库存: {}", voucherId, stock, e);

      Map<String, Object> result = new HashMap<>();
      result.put("success", false);
      result.put("message", "秒杀活动初始化失败: " + e.getMessage());
      result.put("timestamp", System.currentTimeMillis());

      return result;
    }
  }

  /**
   * 获取当前库存信息
   * 
   * @param voucherId 优惠券ID
   * @return 库存信息
   */
  @GetMapping("/stock/{voucherId}")
  public Map<String, Object> getCurrentStock(@PathVariable Long voucherId) {
    log.info("查询当前库存 - 优惠券: {}", voucherId);

    Integer currentStock = seckillService.getCurrentStock(voucherId);

    Map<String, Object> result = new HashMap<>();
    result.put("voucherId", voucherId);
    result.put("currentStock", currentStock);
    result.put("timestamp", System.currentTimeMillis());

    return result;
  }

  /**
   * 获取用户购买数量
   * 
   * @param voucherId 优惠券ID
   * @param userId    用户ID
   * @return 用户购买信息
   */
  @GetMapping("/bought")
  public Map<String, Object> getUserBoughtCount(@RequestParam Long voucherId,
      @RequestParam Long userId) {
    log.info("查询用户购买数量 - 优惠券: {}, 用户: {}", voucherId, userId);

    Integer boughtCount = seckillService.getUserBoughtCount(voucherId, userId);

    Map<String, Object> result = new HashMap<>();
    result.put("voucherId", voucherId);
    result.put("userId", userId);
    result.put("boughtCount", boughtCount);
    result.put("timestamp", System.currentTimeMillis());

    return result;
  }

  /**
   * 健康检查接口
   * 
   * @return 系统状态
   */
  @GetMapping("/health")
  public Map<String, Object> health() {
    Map<String, Object> result = new HashMap<>();
    result.put("status", "UP");
    result.put("service", "seckill-service");
    result.put("timestamp", System.currentTimeMillis());
    return result;
  }

  /**
   * 获取状态文本描述
   * 
   * @param status 状态码
   * @return 状态描述
   */
  private String getStatusText(Integer status) {
    if (status == null) {
      return "订单不存在";
    }

    switch (status) {
      case 0:
        return "处理中";
      case 1:
        return "成功";
      case 2:
        return "失败";
      default:
        return "未知状态";
    }
  }
}
