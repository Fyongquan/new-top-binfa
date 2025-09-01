package com.seckill.utils;

import com.seckill.dto.SeckillRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 测试工具类
 * 提供常用的测试辅助方法
 * 
 * @author seckill-system
 */
public class TestUtils {

  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /**
   * 生成随机用户ID
   */
  public static Long generateRandomUserId() {
    return ThreadLocalRandom.current().nextLong(10000L, 99999L);
  }

  /**
   * 生成随机优惠券ID
   */
  public static Long generateRandomVoucherId() {
    return ThreadLocalRandom.current().nextLong(1000L, 9999L);
  }

  /**
   * 生成随机订单ID
   */
  public static Long generateRandomOrderId() {
    return System.currentTimeMillis() + ThreadLocalRandom.current().nextLong(1000);
  }

  /**
   * 创建秒杀请求
   */
  public static SeckillRequest createSeckillRequest(Long userId, Long voucherId, Integer limit) {
    SeckillRequest request = new SeckillRequest();
    request.setUserId(userId);
    request.setVoucherId(voucherId);
    request.setLimit(limit != null ? limit : 1);
    return request;
  }

  /**
   * 创建HTTP实体（JSON请求）
   */
  public static HttpEntity<SeckillRequest> createJsonEntity(SeckillRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(request, headers);
  }

  /**
   * 格式化时间输出
   */
  public static String formatDateTime(LocalDateTime dateTime) {
    return dateTime != null ? dateTime.format(DATE_FORMAT) : "null";
  }

  /**
   * 计算成功率百分比
   */
  public static String calculateSuccessRate(int successCount, int totalCount) {
    if (totalCount == 0)
      return "0.0%";
    double rate = (double) successCount / totalCount * 100;
    return String.format("%.1f%%", rate);
  }

  /**
   * 格式化耗时显示
   */
  public static String formatDuration(long startTime, long endTime) {
    long duration = endTime - startTime;
    if (duration < 1000) {
      return duration + "ms";
    } else if (duration < 60000) {
      return String.format("%.2f秒", duration / 1000.0);
    } else {
      long minutes = duration / 60000;
      long seconds = (duration % 60000) / 1000;
      return minutes + "分" + seconds + "秒";
    }
  }

  /**
   * 打印测试分隔线
   */
  public static void printSeparator(String title) {
    String separator = "=".repeat(60);
    System.out.println("\n" + separator);
    System.out.println(" " + title);
    System.out.println(separator);
  }

  /**
   * 打印测试小节标题
   */
  public static void printSection(String title) {
    String separator = "-".repeat(40);
    System.out.println("\n" + separator);
    System.out.println("📋 " + title);
    System.out.println(separator);
  }

  /**
   * 打印成功消息
   */
  public static void printSuccess(String message) {
    System.out.println("✅ " + message);
  }

  /**
   * 打印失败消息
   */
  public static void printFailure(String message) {
    System.out.println("❌ " + message);
  }

  /**
   * 打印警告消息
   */
  public static void printWarning(String message) {
    System.out.println("⚠️ " + message);
  }

  /**
   * 打印信息消息
   */
  public static void printInfo(String message) {
    System.out.println("ℹ️ " + message);
  }

  /**
   * 等待指定时间（秒）
   */
  public static void waitSeconds(int seconds) {
    try {
      System.out.println("⏳ 等待 " + seconds + " 秒...");
      Thread.sleep(seconds * 1000L);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("等待被中断", e);
    }
  }

  /**
   * 验证TTL是否在合理范围内
   */
  public static boolean isValidTTL(Long ttl, long expectedSeconds, double tolerancePercent) {
    if (ttl == null || ttl <= 0)
      return false;

    double tolerance = expectedSeconds * tolerancePercent / 100;
    return Math.abs(ttl - expectedSeconds) <= tolerance;
  }

  /**
   * 生成测试用例描述
   */
  public static String generateTestDescription(String testName, Object... params) {
    StringBuilder desc = new StringBuilder();
    desc.append("🧪 ").append(testName);

    if (params.length > 0) {
      desc.append(" [");
      for (int i = 0; i < params.length; i++) {
        if (i > 0)
          desc.append(", ");
        desc.append(params[i]);
      }
      desc.append("]");
    }

    return desc.toString();
  }

  /**
   * 验证测试结果并输出
   */
  public static void assertAndPrint(boolean condition, String successMsg, String failureMsg) {
    if (condition) {
      printSuccess(successMsg);
    } else {
      printFailure(failureMsg);
      throw new AssertionError(failureMsg);
    }
  }

  /**
   * 格式化数据大小
   */
  public static String formatDataSize(long bytes) {
    if (bytes < 1024)
      return bytes + "B";
    if (bytes < 1024 * 1024)
      return String.format("%.1fKB", bytes / 1024.0);
    if (bytes < 1024 * 1024 * 1024)
      return String.format("%.1fMB", bytes / (1024.0 * 1024));
    return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
  }

  /**
   * 计算QPS
   */
  public static double calculateQPS(int requestCount, long durationMs) {
    if (durationMs <= 0)
      return 0;
    return (double) requestCount * 1000 / durationMs;
  }

  /**
   * 格式化QPS显示
   */
  public static String formatQPS(double qps) {
    return String.format("%.2f", qps);
  }
}
