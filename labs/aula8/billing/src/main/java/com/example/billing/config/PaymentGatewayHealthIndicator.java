package com.example.billing.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentGatewayHealthIndicator implements HealthIndicator {

  private static final Logger logger = LoggerFactory.getLogger(PaymentGatewayHealthIndicator.class);

  private final RestClient restClient;

  public PaymentGatewayHealthIndicator(RestClient.Builder restClientBuilder) {
    var requestFactory = new JdkClientHttpRequestFactory();
    requestFactory.setReadTimeout(Duration.ofSeconds(1));
    this.restClient = restClientBuilder
        .baseUrl("http://localhost:9999")
        .requestFactory(requestFactory)
        .build();
  }

  @Override
  public Health health() {
    try {
      restClient.get().uri("/").retrieve().toBodilessEntity();
      logger.info("gateway de pagamento respondendo");
      return Health.up().withDetail("gateway", "http://localhost:9999").build();
    } catch (Exception e) {
      logger.warn("gateway de pagamento indisponível", e);
      return Health.down(e).build();
    }
  }
}