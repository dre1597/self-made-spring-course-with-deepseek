package com.example.operationsclient.fleet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.example.fleetcontract.api.FleetTelemetryGrpc;
import com.example.fleetcontract.api.GetVehicleRequest;
import com.example.fleetcontract.api.Position;
import com.example.fleetcontract.api.StreamPositionsRequest;
import com.example.fleetcontract.api.Vehicle;

import org.springframework.stereotype.Service;

@Service
public class FleetOperationsService {

  private final FleetTelemetryGrpc.FleetTelemetryBlockingStub fleetTelemetry;

  public FleetOperationsService(FleetTelemetryGrpc.FleetTelemetryBlockingStub fleetTelemetry) {
    this.fleetTelemetry = fleetTelemetry;
  }

  public Vehicle findVehicle(String vehicleId) {
    return fleetTelemetry.getVehicle(GetVehicleRequest.newBuilder()
        .setVehicleId(vehicleId)
        .build());
  }

  public List<Position> streamPositions(String vehicleId) {
    Iterator<Position> positions = fleetTelemetry.streamPositions(
        StreamPositionsRequest.newBuilder().setVehicleId(vehicleId).build());
    List<Position> result = new ArrayList<>();
    positions.forEachRemaining(result::add);
    return result;
  }
}