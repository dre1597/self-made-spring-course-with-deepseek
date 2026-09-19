package com.example.propertyplatform.visit;

import java.time.Instant;

public record VisitResponse(Long id, Long propertyId, String visitorName, Instant scheduledAt, String status) {
}