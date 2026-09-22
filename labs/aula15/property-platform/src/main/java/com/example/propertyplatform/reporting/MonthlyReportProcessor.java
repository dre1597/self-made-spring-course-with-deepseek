package com.example.propertyplatform.reporting;

import java.time.YearMonth;
import java.time.ZoneOffset;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class MonthlyReportProcessor implements ItemProcessor<MonthlyVisitRow, MonthlyReportLine> {

  @Override
  public MonthlyReportLine process(MonthlyVisitRow row) {
    String visitorName = row.visitorName() == null ? "" : row.visitorName().trim();
    if (visitorName.isEmpty()) {
      throw new IllegalStateException("Visita sem nome de visitante: " + row.id());
    }
    YearMonth month = YearMonth.from(row.scheduledAt().toInstant().atZone(ZoneOffset.UTC));
    return new MonthlyReportLine(
        row.id(), month.toString(), row.propertyId(), visitorName, row.scheduledAt());
  }
}