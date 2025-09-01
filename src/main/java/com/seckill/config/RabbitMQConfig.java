package com.seckill.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 * 
 * @author seckill-test
 */
@Configuration
public class RabbitMQConfig {

  // 订单处理相关
  public static final String ORDER_EXCHANGE = "seckill.order.exchange";
  public static final String ORDER_QUEUE = "seckill.order.queue";
  public static final String ORDER_ROUTING_KEY = "seckill.order.create";

  // 延迟重试相关
  public static final String DELAY_EXCHANGE = "seckill.order.delay.exchange";
  public static final String DELAY_QUEUE = "seckill.order.delay.queue";
  public static final String RETRY_QUEUE = "seckill.order.retry.queue";

  // 死信队列相关
  public static final String DLX_EXCHANGE = "seckill.order.dlx.exchange";
  public static final String DLX_QUEUE = "seckill.order.dlx.queue";

  /**
   * 配置消息转换器
   */
  @Bean
  public Jackson2JsonMessageConverter messageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  /**
   * 配置RabbitTemplate
   */
  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(messageConverter());

    // 开启发送确认
    template.setMandatory(true);

    return template;
  }

  /**
   * 订单交换机
   */
  @Bean
  public DirectExchange orderExchange() {
    return ExchangeBuilder.directExchange(ORDER_EXCHANGE)
        .durable(true)
        .build();
  }

  /**
   * 订单队列
   */
  @Bean
  public Queue orderQueue() {
    return QueueBuilder.durable(ORDER_QUEUE)
        .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", "dlx")
        .build();
  }

  /**
   * 绑定订单队列到交换机
   */
  @Bean
  public Binding orderBinding() {
    return BindingBuilder.bind(orderQueue())
        .to(orderExchange())
        .with(ORDER_ROUTING_KEY);
  }

  /**
   * 延迟交换机
   */
  @Bean
  public DirectExchange delayExchange() {
    return ExchangeBuilder.directExchange(DELAY_EXCHANGE)
        .durable(true)
        .build();
  }

  /**
   * 延迟队列（TTL队列）
   */
  @Bean
  public Queue delayQueue() {
    return QueueBuilder.durable(DELAY_QUEUE)
        .withArgument("x-dead-letter-exchange", ORDER_EXCHANGE)
        .withArgument("x-dead-letter-routing-key", "seckill.order.retry")
        .build();
  }

  /**
   * 重试队列
   */
  @Bean
  public Queue retryQueue() {
    return QueueBuilder.durable(RETRY_QUEUE)
        .build();
  }

  /**
   * 绑定延迟队列
   */
  @Bean
  public Binding delayBinding() {
    return BindingBuilder.bind(delayQueue())
        .to(delayExchange())
        .with("seckill.order.delay");
  }

  /**
   * 绑定重试队列
   */
  @Bean
  public Binding retryBinding() {
    return BindingBuilder.bind(retryQueue())
        .to(orderExchange())
        .with("seckill.order.retry");
  }

  /**
   * 死信交换机
   */
  @Bean
  public DirectExchange dlxExchange() {
    return ExchangeBuilder.directExchange(DLX_EXCHANGE)
        .durable(true)
        .build();
  }

  /**
   * 死信队列
   */
  @Bean
  public Queue dlxQueue() {
    return QueueBuilder.durable(DLX_QUEUE)
        .build();
  }

  /**
   * 绑定死信队列
   */
  @Bean
  public Binding dlxBinding() {
    return BindingBuilder.bind(dlxQueue())
        .to(dlxExchange())
        .with("dlx");
  }
}
