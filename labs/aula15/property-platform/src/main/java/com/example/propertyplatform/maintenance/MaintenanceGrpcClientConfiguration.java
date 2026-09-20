package com.example.propertyplatform.maintenance;

import com.example.maintenancecontract.api.MaintenanceServiceGrpc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class MaintenanceGrpcClientConfiguration {

  @Bean
  MaintenanceServiceGrpc.MaintenanceServiceBlockingStub maintenanceServiceStub(
      GrpcChannelFactory channels,
      @Value("${app.maintenance.grpc-target}") String grpcTarget) {
    return MaintenanceServiceGrpc.newBlockingStub(channels.createChannel(grpcTarget));
  }
}