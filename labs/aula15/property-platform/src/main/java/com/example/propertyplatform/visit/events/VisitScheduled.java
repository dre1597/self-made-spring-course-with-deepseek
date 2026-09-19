package com.example.propertyplatform.visit.events;

import java.time.Instant;

public record VisitScheduled(Long visitId, Long propertyId, String visitorName, Instant scheduledAt) {
}