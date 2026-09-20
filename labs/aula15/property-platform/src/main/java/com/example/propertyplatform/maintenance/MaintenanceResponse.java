package com.example.propertyplatform.maintenance;

import java.time.Instant;

public record MaintenanceResponse(
    Long id,
    Long propertyId,
    String description,
    String priority,
    String serviceOrderId,
    String status,
    Instant requestedAt) {
}