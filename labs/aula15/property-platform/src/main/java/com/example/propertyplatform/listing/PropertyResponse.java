package com.example.propertyplatform.listing;

import java.math.BigDecimal;

public record PropertyResponse(Long id, String title, String city, BigDecimal monthlyRent, String status) {
}