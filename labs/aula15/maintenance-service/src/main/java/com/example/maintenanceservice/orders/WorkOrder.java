package com.example.maintenanceservice.orders;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "work_orders")
public class WorkOrder {

  @Id
  @Column(nullable = false)
  private String orderId;

  @Column(nullable = false)
  private String propertyId;

  @Column(nullable = false)
  private String description;

  @Column(nullable = false)
  private String priority;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private WorkOrderStatus status;

  @Column(nullable = false)
  private Instant openedAt;

  protected WorkOrder() {
  }

  public WorkOrder(String orderId, String propertyId, String description, String priority) {
    this.orderId = orderId;
    this.propertyId = propertyId;
    this.description = description;
    this.priority = priority;
    this.status = WorkOrderStatus.OPEN;
    this.openedAt = Instant.now();
  }

  public String getOrderId() {
    return orderId;
  }

  public String getPropertyId() {
    return propertyId;
  }

  public String getDescription() {
    return description;
  }

  public String getPriority() {
    return priority;
  }

  public WorkOrderStatus getStatus() {
    return status;
  }

  public Instant getOpenedAt() {
    return openedAt;
  }
}