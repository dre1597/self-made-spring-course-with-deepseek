package com.example.shop.order;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderPlacedKafkaPublisher {

  private final KafkaTemplate<String, OrderPlaced> kafkaTemplate;

  public OrderPlacedKafkaPublisher(KafkaTemplate<String, OrderPlaced> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  public void publish(OrderPlaced event) {
    kafkaTemplate.send("order-placed", event);
  }
}