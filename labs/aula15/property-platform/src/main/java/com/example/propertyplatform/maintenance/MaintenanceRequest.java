package com.example.propertyplatform.maintenance;

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
@Table(name = "maintenance_requests")
public class MaintenanceRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long propertyId;

  @Column(nullable = false)
  private String description;

  @Column(nullable = false)
  private String priority;

  @Column(nullable = false)
  private String serviceOrderId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MaintenanceRequestStatus status;

  @Column(nullable = false)
  private Instant requestedAt;

  protected MaintenanceRequest() {
  }

  public MaintenanceRequest(Long propertyId, String description, String priority, String serviceOrderId) {
    this.propertyId = propertyId;
    this.description = description;
    this.priority = priority;
    this.serviceOrderId = serviceOrderId;
    this.status = MaintenanceRequestStatus.OPEN;
    this.requestedAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public Long getPropertyId() {
    return propertyId;
  }

  public String getDescription() {
    return description;
  }

  public String getPriority() {
    return priority;
  }

  public String getServiceOrderId() {
    return serviceOrderId;
  }

  public MaintenanceRequestStatus getStatus() {
    return status;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }
}