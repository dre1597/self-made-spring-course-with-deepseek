package com.example.shop.order;

import jakarta.persistence.*;

@Entity
@Table(name = "shop_order")
public class Order {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String item;
  private double amount;

  protected Order() {
  }

  public Order(String item, double amount) {
    this.item = item;
    this.amount = amount;
  }

  public Long getId() {
    return id;
  }

  public String getItem() {
    return item;
  }

  public double getAmount() {
    return amount;
  }
}