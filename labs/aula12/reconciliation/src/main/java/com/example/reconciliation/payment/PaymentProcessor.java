package com.example.reconciliation.payment;

import java.math.BigDecimal;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class PaymentProcessor implements ItemProcessor<PaymentRow, PaymentRow> {

  @Override
  public PaymentRow process(PaymentRow row) {
    row.setTransactionId(row.getTransactionId().trim());
    row.setCustomerEmail(row.getCustomerEmail().trim().toLowerCase());
    row.setAmount(row.getAmount().trim().replace(',', '.'));
    row.setStatus(row.getStatus().trim().toUpperCase());
    try {
      new BigDecimal(row.getAmount());
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("Valor inválido na transação " + row.getTransactionId(), exception);
    }
    return row;
  }
}