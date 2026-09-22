package com.example.propertyplatform.maintenance;

import com.example.maintenancecontract.api.MaintenanceServiceGrpc;
import com.example.maintenancecontract.api.OpenServiceOrderRequest;
import com.example.maintenancecontract.api.ServiceOrder;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceRequestService {

  private final MaintenanceRequestRepository repository;

  private final MaintenanceServiceGrpc.MaintenanceServiceBlockingStub maintenanceService;

  private final Counter openedRequests;

  public MaintenanceRequestService(
      MaintenanceRequestRepository repository,
      MaintenanceServiceGrpc.MaintenanceServiceBlockingStub maintenanceService,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.maintenanceService = maintenanceService;
    this.openedRequests = Counter.builder("maintenance.requests.opened")
        .description("Pedidos de manutenção abertos")
        .register(meterRegistry);
  }

  @Transactional
  public MaintenanceRequest open(OpenMaintenanceRequest request) {
    ServiceOrder order = maintenanceService.openServiceOrder(OpenServiceOrderRequest.newBuilder()
        .setPropertyId(String.valueOf(request.propertyId()))
        .setDescription(request.description())
        .setPriority(request.priority())
        .build());
    MaintenanceRequest maintenanceRequest = repository.save(new MaintenanceRequest(
        request.propertyId(), request.description(), request.priority(), order.getOrderId()));
    openedRequests.increment();
    return maintenanceRequest;
  }
}