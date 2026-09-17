package com.example.operationsclient.web;

public record PositionResponse(
    String vehicleId,
    double latitude,
    double longitude,
    long recordedAtEpochSeconds) {
}