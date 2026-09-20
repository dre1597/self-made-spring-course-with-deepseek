package com.example.maintenanceservice.config;

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
public class AmqpConfiguration {

  @Bean
  MessageConverter jacksonAmqpMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  @Bean
  TopicExchange visitExchange() {
    return new TopicExchange("visit-exchange");
  }

  @Bean
  Queue visitScheduledQueue() {
    return QueueBuilder.durable("visit-scheduled")
        .deadLetterExchange("visit-dlx")
        .deadLetterRoutingKey("visit.scheduled.dead")
        .build();
  }

  @Bean
  Binding visitScheduledBinding(Queue visitScheduledQueue, TopicExchange visitExchange) {
    return BindingBuilder.bind(visitScheduledQueue).to(visitExchange).with("visit.scheduled");
  }
}