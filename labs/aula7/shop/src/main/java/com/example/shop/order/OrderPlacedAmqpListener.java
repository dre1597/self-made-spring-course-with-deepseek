package com.example.shop.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedAmqpListener {

  private static final Logger logger = LoggerFactory.getLogger(OrderPlacedAmqpListener.class);

  @RabbitListener(queues = "order-created")
  public void on(OrderPlaced event) {
    // processa o evento vindo de outro serviço
    logger.info("RabbitMQ entregou {}", event);
  }
}