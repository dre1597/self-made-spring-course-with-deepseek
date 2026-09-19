package com.example.propertyplatform.listing;

public class PropertyNotFoundException extends RuntimeException {

  public PropertyNotFoundException(Long id) {
    super("Imóvel não encontrado: " + id);
  }
}