package com.example.reconciliation.payment;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class PaymentWriter implements ItemWriter<PaymentRow> {

  private final JdbcClient jdbcClient;

  public PaymentWriter(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public void write(Chunk<? extends PaymentRow> chunk) {
    for (PaymentRow row : chunk) {
      jdbcClient.sql("""
                    MERGE INTO reconciled_payments
                        (transaction_id, customer_email, amount, status, reconciled_at)
                    KEY (transaction_id)
                    VALUES (:transactionId, :customerEmail, :amount, :status, :reconciledAt)
                    """)
          .param("transactionId", row.getTransactionId())
          .param("customerEmail", row.getCustomerEmail())
          .param("amount", new BigDecimal(row.getAmount()))
          .param("status", row.getStatus())
          .param("reconciledAt", Instant.now())
          .update();
    }
  }
}