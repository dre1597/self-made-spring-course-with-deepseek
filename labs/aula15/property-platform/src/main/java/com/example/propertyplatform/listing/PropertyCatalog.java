package com.example.propertyplatform.listing;

import java.math.BigDecimal;
import java.util.List;

public interface PropertyCatalog {

  PropertySummary getById(Long id);

  List<PropertySummary> findAvailable(String city, BigDecimal maxMonthlyRent);
}