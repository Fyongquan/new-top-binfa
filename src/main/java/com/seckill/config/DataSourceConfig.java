package com.seckill.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 数据源配置类
 * 使用Spring Boot自动配置的HikariCP数据源
 * 
 * @author seckill-test
 */
@Configuration
@EnableTransactionManagement
public class DataSourceConfig {

  // 使用Spring Boot自动配置，配置在application.yml中

}
