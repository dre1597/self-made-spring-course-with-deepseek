package com.example.propertyplatform.visit;

import java.time.Instant;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScheduleVisitRequest(
    @NotNull Long propertyId,
    @NotBlank String visitorName,
    @Future Instant scheduledAt) {
}