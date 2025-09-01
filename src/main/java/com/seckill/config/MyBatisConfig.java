package com.seckill.config;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import jakarta.annotation.Resource;
import javax.sql.DataSource;

/**
 * MyBatis配置类
 * 
 * @author seckill-test
 */
@Configuration
public class MyBatisConfig {

  @Resource
  private DataSource dataSource;

  /**
   * 配置SqlSessionFactory
   */
  @Bean
  public SqlSessionFactory sqlSessionFactory() throws Exception {
    SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
    factoryBean.setDataSource(dataSource);

    // 设置MyBatis配置文件位置
    factoryBean.setConfigLocation(
        new PathMatchingResourcePatternResolver().getResource("classpath:mybatis-config.xml"));

    // 设置Mapper XML文件位置
    factoryBean.setMapperLocations(
        new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));

    // 设置实体类包路径
    factoryBean.setTypeAliasesPackage("com.seckill.entity");

    return factoryBean.getObject();
  }

  /**
   * 配置SqlSessionTemplate
   */
  @Bean
  public SqlSessionTemplate sqlSessionTemplate() throws Exception {
    return new SqlSessionTemplate(sqlSessionFactory());
  }
}
