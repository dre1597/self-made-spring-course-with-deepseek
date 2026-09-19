package com.example.propertyplatform.config;

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
  TopicExchange visitExchange() {
    return new TopicExchange("visit-exchange");
  }

  @Bean
  TopicExchange visitDeadLetterExchange() {
    return new TopicExchange("visit-dlx");
  }

  @Bean
  Queue visitScheduledQueue() {
    return QueueBuilder.durable("visit-scheduled")
        .deadLetterExchange("visit-dlx")
        .deadLetterRoutingKey("visit.scheduled.dead")
        .build();
  }

  @Bean
  Queue visitScheduledDeadLetterQueue() {
    return new Queue("visit-scheduled.dlq");
  }

  @Bean
  Binding visitScheduledBinding(Queue visitScheduledQueue, TopicExchange visitExchange) {
    return BindingBuilder.bind(visitScheduledQueue).to(visitExchange).with("visit.scheduled");
  }

  @Bean
  Binding visitScheduledDeadLetterBinding(
      Queue visitScheduledDeadLetterQueue, TopicExchange visitDeadLetterExchange) {
    return BindingBuilder.bind(visitScheduledDeadLetterQueue)
        .to(visitDeadLetterExchange)
        .with("visit.scheduled.dead");
  }
}