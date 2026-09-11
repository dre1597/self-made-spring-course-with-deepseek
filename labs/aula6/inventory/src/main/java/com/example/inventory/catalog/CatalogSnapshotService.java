package com.example.inventory.catalog;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;

@Service
public class CatalogSnapshotService {

  private static final Logger logger = LoggerFactory.getLogger(CatalogSnapshotService.class);

  @ConcurrencyLimit(limit = 2)
  public void rebuild(Long productId) {
    logger.info("Snapshot do catálogo iniciado pra {} às {}", productId, Instant.now());
    // leitura pesada que não aguenta muitas threads simultâneas
    try {
      Thread.sleep(2000);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
    logger.info("Snapshot do catálogo concluído pra {} às {}", productId, Instant.now());
  }
}