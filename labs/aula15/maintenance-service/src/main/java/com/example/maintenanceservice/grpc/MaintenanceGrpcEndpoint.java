package com.example.maintenanceservice.grpc;

import com.example.maintenancecontract.api.GetServiceOrderRequest;
import com.example.maintenancecontract.api.MaintenanceServiceGrpc;
import com.example.maintenancecontract.api.OpenServiceOrderRequest;
import com.example.maintenancecontract.api.ServiceOrder;
import com.example.maintenanceservice.orders.WorkOrder;
import com.example.maintenanceservice.orders.WorkOrderService;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class MaintenanceGrpcEndpoint extends MaintenanceServiceGrpc.MaintenanceServiceImplBase {

  private final WorkOrderService workOrderService;

  public MaintenanceGrpcEndpoint(WorkOrderService workOrderService) {
    this.workOrderService = workOrderService;
  }

  @Override
  public void openServiceOrder(OpenServiceOrderRequest request, StreamObserver<ServiceOrder> responseObserver) {
    WorkOrder workOrder = workOrderService.open(
        request.getPropertyId(), request.getDescription(), request.getPriority());
    responseObserver.onNext(toMessage(workOrder));
    responseObserver.onCompleted();
  }

  @Override
  public void getServiceOrder(GetServiceOrderRequest request, StreamObserver<ServiceOrder> responseObserver) {
    workOrderService.find(request.getOrderId()).ifPresentOrElse(
        workOrder -> {
          responseObserver.onNext(toMessage(workOrder));
          responseObserver.onCompleted();
        },
        () -> responseObserver.onError(Status.NOT_FOUND
            .withDescription("Ordem não encontrada")
            .asRuntimeException()));
  }

  private static ServiceOrder toMessage(WorkOrder workOrder) {
    return ServiceOrder.newBuilder()
        .setOrderId(workOrder.getOrderId())
        .setPropertyId(workOrder.getPropertyId())
        .setDescription(workOrder.getDescription())
        .setPriority(workOrder.getPriority())
        .setStatus(workOrder.getStatus().name())
        .setOpenedAtEpochSeconds(workOrder.getOpenedAt().getEpochSecond())
        .build();
  }
}