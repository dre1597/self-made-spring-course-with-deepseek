package com.example.inventory.vendor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VendorStubController {

  private final Map<Long, AtomicInteger> priceAttempts = new ConcurrentHashMap<>();

  @GetMapping("/api/vendor/prices/{productId}")
  public ResponseEntity<Price> price(@PathVariable Long productId) {
    int attempt = priceAttempts.computeIfAbsent(productId, id -> new AtomicInteger()).incrementAndGet();
    if (attempt <= 2) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
    return ResponseEntity.ok(new Price(productId, "BRL", 29.90));
  }

  private final AtomicInteger orderAttempts = new AtomicInteger();

  @PostMapping("/api/vendor/orders")
  public ResponseEntity<Void> orders() {
    if (orderAttempts.incrementAndGet() <= 2) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
    return ResponseEntity.accepted().build();
  }
}