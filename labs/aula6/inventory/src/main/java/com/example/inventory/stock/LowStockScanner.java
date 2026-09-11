package com.example.inventory.stock;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LowStockScanner {

  private static final Logger logger = LoggerFactory.getLogger(LowStockScanner.class);

  @Scheduled(cron = "0 0 8 * * MON-FRI")
  public void scan() {
    logger.info("Varredura de estoque baixo às {}", Instant.now());
  }
}