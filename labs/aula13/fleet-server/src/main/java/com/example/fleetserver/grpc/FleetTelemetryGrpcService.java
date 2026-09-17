package com.example.fleetserver.grpc;

import io.grpc.stub.StreamObserver;
import io.grpc.Status;

import com.example.fleetcontract.api.FleetTelemetryGrpc;
import com.example.fleetcontract.api.GetVehicleRequest;
import com.example.fleetcontract.api.Position;
import com.example.fleetcontract.api.StreamPositionsRequest;
import com.example.fleetcontract.api.Vehicle;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class FleetTelemetryGrpcService extends FleetTelemetryGrpc.FleetTelemetryImplBase {

  @Override
  public void getVehicle(GetVehicleRequest request, StreamObserver<Vehicle> responseObserver) {
    if (!request.getVehicleId().equals("bike-001")) {
      responseObserver.onError(Status.NOT_FOUND
          .withDescription("Bicicleta não encontrada")
          .asRuntimeException());
      return;
    }
    responseObserver.onNext(Vehicle.newBuilder()
        .setVehicleId("bike-001")
        .setStatus("AVAILABLE")
        .setBatteryPercentage(87)
        .build());
    responseObserver.onCompleted();
  }

  @Override
  public void streamPositions(StreamPositionsRequest request, StreamObserver<Position> responseObserver) {
    for (int index = 0; index < 3; index++) {
      responseObserver.onNext(Position.newBuilder()
          .setVehicleId(request.getVehicleId())
          .setLatitude(-22.90 + index * 0.001)
          .setLongitude(-43.17 + index * 0.001)
          .setRecordedAtEpochSeconds(System.currentTimeMillis() / 1000)
          .build());
    }
    responseObserver.onCompleted();
  }
}