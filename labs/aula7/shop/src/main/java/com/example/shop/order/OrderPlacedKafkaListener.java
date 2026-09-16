package com.example.shop.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedKafkaListener {

  private static final Logger logger = LoggerFactory.getLogger(OrderPlacedKafkaListener.class);

  @KafkaListener(topics = "order-placed")
  public void on(OrderPlaced event) {
    // processa o evento vindo de outro serviço
    logger.info("Kafka entregou {}", event);
  }
}