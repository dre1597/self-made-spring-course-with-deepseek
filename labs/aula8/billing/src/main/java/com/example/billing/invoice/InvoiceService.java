package com.example.billing.invoice;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;

import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

  private final Counter createdInvoices;

  public InvoiceService(MeterRegistry meterRegistry) {
    this.createdInvoices = Counter.builder("billing.invoices.created")
        .description("Faturas criadas")
        .register(meterRegistry);
  }

  @Observed(name = "billing.create-invoice")
  public void invoiceCreated() {
    createdInvoices.increment();
  }
}