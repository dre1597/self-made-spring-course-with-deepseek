package com.example.shop.order;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedIntegrationListener {

  @ApplicationModuleListener
  public void on(OrderPlaced event) {
    // integração entre módulos, em transação própria
  }
}