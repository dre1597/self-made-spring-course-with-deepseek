package com.example.billing.invoice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvoiceController {

  private final InvoiceService invoiceService;

  public InvoiceController(InvoiceService invoiceService) {
    this.invoiceService = invoiceService;
  }

  @PostMapping("/api/invoices")
  public ResponseEntity<Void> create() {
    invoiceService.invoiceCreated();
    return ResponseEntity.accepted().build();
  }
}