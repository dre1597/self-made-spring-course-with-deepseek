package com.example.reports.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class FileReportWriter implements ReportWriter {

  @Override
  public void write(Report report) {
    Path destination = Path.of("build", "reports", report.title() + ".txt");
    try {
      Files.createDirectories(destination.getParent());
      Files.writeString(destination, report.content());
    } catch (IOException exception) {
      throw new IllegalStateException("Não conseguiu gravar o relatório em " + destination, exception);
    }
  }
}