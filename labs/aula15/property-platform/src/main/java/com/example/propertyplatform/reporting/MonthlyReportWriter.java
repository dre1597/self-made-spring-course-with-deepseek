package com.example.propertyplatform.reporting;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class MonthlyReportWriter implements ItemWriter<MonthlyReportLine> {

  private final JdbcClient jdbcClient;

  public MonthlyReportWriter(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public void write(Chunk<? extends MonthlyReportLine> chunk) {
    for (MonthlyReportLine line : chunk) {
      jdbcClient.sql("""
                    MERGE INTO monthly_report_lines
                        (visit_id, report_month, property_id, visitor_name, scheduled_at, generated_at)
                    KEY (visit_id)
                    VALUES (:visitId, :reportMonth, :propertyId, :visitorName, :scheduledAt, :generatedAt)
                    """)
          .param("visitId", line.visitId())
          .param("reportMonth", line.reportMonth())
          .param("propertyId", line.propertyId())
          .param("visitorName", line.visitorName())
          .param("scheduledAt", line.scheduledAt())
          .param("generatedAt", Timestamp.from(Instant.now()))
          .update();
    }
  }
}