package com.example.operationsclient.web;

import java.util.List;

import com.example.fleetcontract.api.Position;
import com.example.fleetcontract.api.Vehicle;
import com.example.operationsclient.fleet.FleetOperationsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fleet")
public class FleetOperationsController {

  private final FleetOperationsService fleetOperations;

  public FleetOperationsController(FleetOperationsService fleetOperations) {
    this.fleetOperations = fleetOperations;
  }

  @GetMapping("/{vehicleId}")
  public VehicleResponse vehicle(@PathVariable String vehicleId) {
    Vehicle vehicle = fleetOperations.findVehicle(vehicleId);
    return new VehicleResponse(
        vehicle.getVehicleId(),
        vehicle.getStatus(),
        vehicle.getBatteryPercentage());
  }

  @GetMapping("/{vehicleId}/positions")
  public List<PositionResponse> positions(@PathVariable String vehicleId) {
    return fleetOperations.streamPositions(vehicleId).stream()
        .map(FleetOperationsController::toResponse)
        .toList();
  }

  private static PositionResponse toResponse(Position position) {
    return new PositionResponse(
        position.getVehicleId(),
        position.getLatitude(),
        position.getLongitude(),
        position.getRecordedAtEpochSeconds());
  }
}