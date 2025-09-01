package com.seckill;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 秒杀系统启动类
 * 
 * @author seckill-test
 */
@SpringBootApplication
@MapperScan("com.seckill.mapper")
public class SeckillApplication {

  public static void main(String[] args) {
    SpringApplication.run(SeckillApplication.class, args);
    System.out.println("=================================");
    System.out.println("🚀 秒杀系统启动成功！");
    System.out.println("🌐 应用地址: http://localhost:8080");
    System.out.println("🏥 健康检查: http://localhost:8080/api/seckill/health");
    System.out.println("🐰 RabbitMQ管理: http://localhost:15672 (guest/guest)");
    System.out.println("📊 Redis管理: http://localhost:8081");
    System.out.println("=================================");
  }
}
