package com.example.propertyplatform.listing;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "properties")
@EntityListeners(AuditingEntityListener.class)
public class Property {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private String city;

  @Column(nullable = false)
  private BigDecimal monthlyRent;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ListingStatus status;

  @CreatedDate
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected Property() {
  }

  public Property(String title, String city, BigDecimal monthlyRent) {
    this.title = title;
    this.city = city;
    this.monthlyRent = monthlyRent;
    this.status = ListingStatus.ACTIVE;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getCity() {
    return city;
  }

  public BigDecimal getMonthlyRent() {
    return monthlyRent;
  }

  public ListingStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}