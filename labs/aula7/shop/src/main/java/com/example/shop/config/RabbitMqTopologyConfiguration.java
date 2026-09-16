package com.example.shop.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqTopologyConfiguration {

  @Bean
  MessageConverter jacksonAmqpMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  @Bean
  TopicExchange orderExchange() {
    return new TopicExchange("order-exchange");
  }

  @Bean
  TopicExchange orderDeadLetterExchange() {
    return new TopicExchange("order-dlx");
  }

  @Bean
  Queue orderCreatedQueue() {
    return QueueBuilder.durable("order-created")
        .deadLetterExchange("order-dlx")
        .deadLetterRoutingKey("order.created.dead")
        .build();
  }

  @Bean
  Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange orderExchange) {
    return BindingBuilder.bind(orderCreatedQueue).to(orderExchange).with("order.created");
  }

  @Bean
  Queue orderCreatedDeadLetterQueue() {
    return new Queue("order-created.dlq");
  }

  @Bean
  Binding deadLetterBinding(Queue orderCreatedDeadLetterQueue, TopicExchange orderDeadLetterExchange) {
    return BindingBuilder.bind(orderCreatedDeadLetterQueue)
        .to(orderDeadLetterExchange)
        .with("order.created.dead");
  }
}