package com.example.books.book;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/prices")
public interface PriceClient {

  @GetExchange("/{bookId}")
  PriceResponse findPrice(@PathVariable Long bookId);

  record PriceResponse(Long bookId, String currency, double amount) {
  }
}