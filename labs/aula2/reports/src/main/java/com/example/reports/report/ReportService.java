package com.example.reports.report;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

  private final ObjectProvider<ReportGenerator> reportGenerators;
  private final ReportWriter reportWriter;

  public ReportService(ObjectProvider<ReportGenerator> reportGenerators, ReportWriter reportWriter) {
    this.reportGenerators = reportGenerators;
    this.reportWriter = reportWriter;
  }

  public void generateAndWrite(String title, String content) {
    ReportGenerator generator = reportGenerators.getObject();
    Report report = generator.generate(title, content);
    reportWriter.write(report);
  }
}