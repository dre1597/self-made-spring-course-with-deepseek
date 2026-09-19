package com.example.propertyplatform.listing;

import java.math.BigDecimal;

public record PropertySummary(Long id, String title, String city, BigDecimal monthlyRent) {
}