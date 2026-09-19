package com.example.propertyplatform.visit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "visits")
public class Visit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long propertyId;

  @Column(nullable = false)
  private String visitorName;

  @Column(nullable = false)
  private Instant scheduledAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private VisitStatus status;

  protected Visit() {
  }

  public Visit(Long propertyId, String visitorName, Instant scheduledAt) {
    this.propertyId = propertyId;
    this.visitorName = visitorName;
    this.scheduledAt = scheduledAt;
    this.status = VisitStatus.SCHEDULED;
  }

  public Long getId() {
    return id;
  }

  public Long getPropertyId() {
    return propertyId;
  }

  public String getVisitorName() {
    return visitorName;
  }

  public Instant getScheduledAt() {
    return scheduledAt;
  }

  public VisitStatus getStatus() {
    return status;
  }
}