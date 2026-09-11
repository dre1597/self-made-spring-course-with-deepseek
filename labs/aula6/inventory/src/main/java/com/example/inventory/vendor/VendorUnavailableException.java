package com.example.inventory.vendor;

public class VendorUnavailableException extends RuntimeException {

  public VendorUnavailableException(Long productId, Throwable cause) {
    super("Fornecedor indisponível ao buscar o preço do produto " + productId, cause);
  }
}