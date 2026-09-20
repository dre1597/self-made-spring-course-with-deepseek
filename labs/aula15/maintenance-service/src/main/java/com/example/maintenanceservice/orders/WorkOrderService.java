package com.example.maintenanceservice.orders;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkOrderService {

  private final WorkOrderRepository repository;

  public WorkOrderService(WorkOrderRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public WorkOrder open(String propertyId, String description, String priority) {
    String orderId = "OS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    return repository.save(new WorkOrder(orderId, propertyId, description, priority));
  }

  @Transactional(readOnly = true)
  public Optional<WorkOrder> find(String orderId) {
    return repository.findById(orderId);
  }
}