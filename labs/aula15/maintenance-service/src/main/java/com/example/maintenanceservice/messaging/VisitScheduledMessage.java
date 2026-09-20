package com.example.maintenanceservice.messaging;

import java.time.Instant;

public record VisitScheduledMessage(Long visitId, Long propertyId, String visitorName, Instant scheduledAt) {
}