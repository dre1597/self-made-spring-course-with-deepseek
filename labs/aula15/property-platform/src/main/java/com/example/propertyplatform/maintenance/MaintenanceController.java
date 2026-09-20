package com.example.propertyplatform.maintenance;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenance-requests")
public class MaintenanceController {

  private final MaintenanceRequestService maintenanceRequestService;

  public MaintenanceController(MaintenanceRequestService maintenanceRequestService) {
    this.maintenanceRequestService = maintenanceRequestService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public MaintenanceResponse open(@Valid @RequestBody OpenMaintenanceRequest request) {
    MaintenanceRequest maintenanceRequest = maintenanceRequestService.open(request);
    return new MaintenanceResponse(
        maintenanceRequest.getId(),
        maintenanceRequest.getPropertyId(),
        maintenanceRequest.getDescription(),
        maintenanceRequest.getPriority(),
        maintenanceRequest.getServiceOrderId(),
        maintenanceRequest.getStatus().name(),
        maintenanceRequest.getRequestedAt());
  }
}