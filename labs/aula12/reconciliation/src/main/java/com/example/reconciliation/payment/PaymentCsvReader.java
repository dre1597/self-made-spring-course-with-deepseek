package com.example.reconciliation.payment;

import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class PaymentCsvReader {

  public FlatFileItemReader<PaymentRow> reader() {
    return new FlatFileItemReaderBuilder<PaymentRow>()
        .name("paymentCsvReader")
        .resource(new ClassPathResource("payments.csv"))
        .delimited()
        .names("transactionId", "customerEmail", "amount", "status")
        .targetType(PaymentRow.class)
        .build();
  }
}