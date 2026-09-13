package com.example.shop.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderCreatedListener {

  private static final Logger logger = LoggerFactory.getLogger(OrderCreatedListener.class);

//  @EventListener // roda na mesma transaction do publisher
  @TransactionalEventListener  // toda só depois do commit
  public void on(OrderPlaced event) {
    // análise, cache, log...
    logger.info("Pedido {} registrado no processo", event.orderId());
  }
}