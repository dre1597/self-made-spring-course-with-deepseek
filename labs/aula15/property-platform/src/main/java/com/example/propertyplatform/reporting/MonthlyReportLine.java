package com.example.propertyplatform.reporting;

import java.sql.Timestamp;

public record MonthlyReportLine(
    Long visitId,
    String reportMonth,
    Long propertyId,
    String visitorName,
    Timestamp scheduledAt) {
}