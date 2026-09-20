package com.example.propertyplatform.maintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OpenMaintenanceRequest(
    @NotNull Long propertyId,
    @NotBlank String description,
    @NotBlank String priority) {
}