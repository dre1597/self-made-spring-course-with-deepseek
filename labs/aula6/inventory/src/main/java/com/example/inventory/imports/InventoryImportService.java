package com.example.inventory.imports;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class InventoryImportService {

  private static final Logger logger = LoggerFactory.getLogger(InventoryImportService.class);

  @Async
  public CompletableFuture<Void> importInBackground(String source) {
    logger.info("Importando {} na thread {}", source, Thread.currentThread());
    process(source);
    return CompletableFuture.completedFuture(null);
  }

  private void process(String source) {
    // trabalho demorado: baixar e gravar o catálogo do fornecedor
  }
}