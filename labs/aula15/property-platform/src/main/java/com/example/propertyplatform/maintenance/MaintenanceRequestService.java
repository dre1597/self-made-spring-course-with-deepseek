package com.example.propertyplatform.maintenance;

import com.example.maintenancecontract.api.MaintenanceServiceGrpc;
import com.example.maintenancecontract.api.OpenServiceOrderRequest;
import com.example.maintenancecontract.api.ServiceOrder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceRequestService {

  private final MaintenanceRequestRepository repository;

  private final MaintenanceServiceGrpc.MaintenanceServiceBlockingStub maintenanceService;

  public MaintenanceRequestService(
      MaintenanceRequestRepository repository,
      MaintenanceServiceGrpc.MaintenanceServiceBlockingStub maintenanceService) {
    this.repository = repository;
    this.maintenanceService = maintenanceService;
  }

  @Transactional
  public MaintenanceRequest open(OpenMaintenanceRequest request) {
    ServiceOrder order = maintenanceService.openServiceOrder(OpenServiceOrderRequest.newBuilder()
        .setPropertyId(String.valueOf(request.propertyId()))
        .setDescription(request.description())
        .setPriority(request.priority())
        .build());
    return repository.save(new MaintenanceRequest(
        request.propertyId(), request.description(), request.priority(), order.getOrderId()));
  }
}