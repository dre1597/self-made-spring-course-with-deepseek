package com.example.inventory.vendor;

public record Price(Long productId, String currency, double amount) {
}