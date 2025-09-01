package com.seckill.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置类
 * 
 * @author seckill-test
 */
@Configuration
public class RedisConfig {

  /**
   * 配置RedisTemplate
   */
  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    // 设置key的序列化方式
    template.setKeySerializer(new StringRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());

    // 设置value的序列化方式
    template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
    template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

    template.afterPropertiesSet();
    return template;
  }

  /**
   * 秒杀Lua脚本
   */
  @Bean("seckillScript")
  public DefaultRedisScript<Long> seckillScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("lua/seckill.lua"));
    script.setResultType(Long.class);
    return script;
  }

  /**
   * 库存回滚Lua脚本
   */
  @Bean("recoverStockScript")
  public DefaultRedisScript<Long> recoverStockScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("lua/recover_stock.lua"));
    script.setResultType(Long.class);
    return script;
  }

  @Bean("testScript")
  public DefaultRedisScript<String> testScript() {
    DefaultRedisScript<String> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("lua/test.lua"));
    script.setResultType(String.class);
    return script;
  }

  @Bean("seckillDebugScript")
  public DefaultRedisScript<Long> seckillDebugScript() {
    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource("lua/seckill-debug.lua"));
    script.setResultType(Long.class);
    return script;
  }
}
