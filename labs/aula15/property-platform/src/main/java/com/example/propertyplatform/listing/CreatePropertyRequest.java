package com.example.propertyplatform.listing;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreatePropertyRequest(
    @NotBlank String title,
    @NotBlank String city,
    @Positive BigDecimal monthlyRent) {
}