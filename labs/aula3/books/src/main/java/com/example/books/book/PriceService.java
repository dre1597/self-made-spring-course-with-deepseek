package com.example.books.book;

import org.springframework.stereotype.Service;

@Service
public class PriceService {

  private final PriceClient priceClient;

  public PriceService(PriceClient priceClient) {
    this.priceClient = priceClient;
  }

  public PriceClient.PriceResponse findPrice(Long bookId) {
    return priceClient.findPrice(bookId);
  }
}