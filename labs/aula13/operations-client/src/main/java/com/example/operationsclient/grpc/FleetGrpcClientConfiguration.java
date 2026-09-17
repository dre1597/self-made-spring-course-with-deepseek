package com.example.operationsclient.grpc;

import com.example.fleetcontract.api.FleetTelemetryGrpc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class FleetGrpcClientConfiguration {

  @Bean
  FleetTelemetryGrpc.FleetTelemetryBlockingStub fleetTelemetryStub(GrpcChannelFactory channels) {
    return FleetTelemetryGrpc.newBlockingStub(channels.createChannel("localhost:9090"));
  }
}