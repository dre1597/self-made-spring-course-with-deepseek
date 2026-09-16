package com.example.shop.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class InvoiceRequestListener {

  private static final Logger logger = LoggerFactory.getLogger(InvoiceRequestListener.class);

  @JmsListener(destination = "invoice-generation")
  public void on(OrderPlaced event) {
    // gera a nota fiscal do pedido
    logger.info("Nota fiscal gerada pra {}", event);
  }

  // recebimento sincrono com timeout para puxar na mão
  //  Optional<OrderPlaced> received = jmsClient.destination("invoice-generation")
  //      .withReceiveTimeout(1000)
  //      .receive(OrderPlaced.class);
}