package com.example.shop.order;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderPlacedAmqpPublisher {

  private final RabbitTemplate rabbitTemplate;

  public OrderPlacedAmqpPublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  public void publish(OrderPlaced event) {
    rabbitTemplate.convertAndSend("order-exchange", "order.created", event);
  }
}