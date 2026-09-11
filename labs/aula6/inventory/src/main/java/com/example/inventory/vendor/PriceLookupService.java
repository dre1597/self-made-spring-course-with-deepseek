package com.example.inventory.vendor;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
public class PriceLookupService {

  private final RestClient restClient;

  public PriceLookupService(RestClient.Builder builder) {
    this.restClient = builder.baseUrl("http://localhost:8080").build();
  }

  @Retryable(
      includes = VendorUnavailableException.class,
      maxRetries = 4,
      delay = 500,
      multiplier = 2,
      maxDelay = 4000)
  public Price findPrice(Long productId) {
    try {
      return restClient.get()
          .uri("/api/vendor/prices/{productId}", productId)
          .retrieve()
          .body(Price.class);
    } catch (ResourceAccessException | HttpServerErrorException exception) {
      throw new VendorUnavailableException(productId, exception);
    }
  }
}