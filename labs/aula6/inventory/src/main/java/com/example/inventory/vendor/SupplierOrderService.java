package com.example.inventory.vendor;

import java.time.Duration;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
public class SupplierOrderService {

  private final RestClient restClient;

  public SupplierOrderService(RestClient.Builder builder) {
    this.restClient = builder.baseUrl("http://localhost:8080").build();
  }

  public void placeOrder(OrderRequest request) {
    RetryTemplate retryTemplate = new RetryTemplate(
        RetryPolicy.builder()
            .includes(ResourceAccessException.class, HttpServerErrorException.class)
            .maxRetries(3)
            .delay(Duration.ofSeconds(1))
            .multiplier(2)
            .maxDelay(Duration.ofSeconds(5))
            .build());

    retryTemplate.invoke(() -> {
      restClient.post()
          .uri("/api/vendor/orders")
          .body(request)
          .retrieve()
          .toBodilessEntity();
      return null;
    });
  }

  public record OrderRequest(Long productId, int quantity) {
  }
}