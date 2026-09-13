package com.example.shop.order;

public record OrderPlaced(Long orderId, String item, double amount) {
}