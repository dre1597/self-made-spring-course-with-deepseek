package com.example.supportassistant.orders;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class OrderController {

  private final OrderAssistantService orderAssistantService;

  public OrderController(OrderAssistantService orderAssistantService) {
    this.orderAssistantService = orderAssistantService;
  }

  @PostMapping("/orders")
  public OrderAnswer orders(@RequestBody OrderRequest request) {
    return new OrderAnswer(orderAssistantService.askOrders(request.message()));
  }

  public record OrderRequest(String message) {
  }

  public record OrderAnswer(String answer) {
  }
}