package com.example.reports.report;

import java.time.Instant;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class ReportGenerator {

  private final Instant startedAt = Instant.now();

  public Report generate(String title, String content) {
    return new Report(title, content, startedAt);
  }
}