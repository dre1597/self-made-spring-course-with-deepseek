package com.example.propertyplatform.maintenance;

import com.example.maintenancecontract.api.GetServiceOrderRequest;
import com.example.maintenancecontract.api.MaintenanceServiceGrpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceServiceHealthIndicator implements HealthIndicator {

  private final MaintenanceServiceGrpc.MaintenanceServiceBlockingStub maintenanceService;

  public MaintenanceServiceHealthIndicator(
      MaintenanceServiceGrpc.MaintenanceServiceBlockingStub maintenanceService) {
    this.maintenanceService = maintenanceService;
  }

  @Override
  public Health health() {
    try {
      maintenanceService.getServiceOrder(GetServiceOrderRequest.newBuilder()
          .setOrderId("health-check")
          .build());
      return Health.up().withDetail("maintenance-service", "reachable").build();
    } catch (StatusRuntimeException exception) {
      if (exception.getStatus().getCode() == Status.Code.NOT_FOUND) {
        return Health.up().withDetail("maintenance-service", "reachable").build();
      }
      return Health.down(exception).build();
    }
  }
}