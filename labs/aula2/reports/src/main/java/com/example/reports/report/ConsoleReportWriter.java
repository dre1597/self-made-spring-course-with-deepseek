package com.example.reports.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class ConsoleReportWriter implements ReportWriter {

  private static final Logger logger = LoggerFactory.getLogger(ConsoleReportWriter.class);

  @Override
  public void write(Report report) {
    logger.info("Relatório '{}' ({}): {}", report.title(), report.startedAt(), report.content());
  }
}