package com.example.propertyplatform.reporting;

import java.sql.Timestamp;

public record MonthlyVisitRow(Long id, Long propertyId, String visitorName, Timestamp scheduledAt) {
}