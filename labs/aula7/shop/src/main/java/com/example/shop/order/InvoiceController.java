package com.example.shop.order;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InvoiceController {

  private final InvoiceRequestService invoiceService;

  public InvoiceController(InvoiceRequestService invoiceService) {
    this.invoiceService = invoiceService;
  }

  @PostMapping("/jms/invoices")
  public ResponseEntity<Void> requestInvoice(@RequestBody OrderPlaced event) {
    invoiceService.request(event);
    return ResponseEntity.accepted().build();
  }
}