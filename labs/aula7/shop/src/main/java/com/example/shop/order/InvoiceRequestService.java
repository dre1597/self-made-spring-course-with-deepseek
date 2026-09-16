package com.example.shop.order;

import org.springframework.jms.core.JmsClient;
import org.springframework.stereotype.Service;

@Service
public class InvoiceRequestService {

  private final JmsClient jmsClient;

  public InvoiceRequestService(JmsClient jmsClient) {
    this.jmsClient = jmsClient;
  }

  public void request(OrderPlaced event) {
    jmsClient.destination("invoice-generation")
        .send(event);
  }
}