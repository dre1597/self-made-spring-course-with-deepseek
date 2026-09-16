package com.example.shop.order;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OrderAmqpController {

  private final OrderPlacedAmqpPublisher publisher;

  public OrderAmqpController(OrderPlacedAmqpPublisher publisher) {
    this.publisher = publisher;
  }

  @PostMapping("/rabbit/publish")
  public ResponseEntity<Void> publish(@RequestBody OrderPlaced event) {
    publisher.publish(event);
    return ResponseEntity.accepted().build();
  }
}