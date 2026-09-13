package com.example.shop.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OrderController {

  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @PostMapping("/orders")
  public ResponseEntity<Order> create(@RequestBody CreateOrderRequest request) {
    Order order = orderService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(order);
  }
}