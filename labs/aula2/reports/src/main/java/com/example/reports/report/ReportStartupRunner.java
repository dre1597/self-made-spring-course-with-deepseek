package com.example.reports.report;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ReportStartupRunner implements ApplicationRunner {

  private final ReportService reportService;

  public ReportStartupRunner(ReportService reportService) {
    this.reportService = reportService;
  }

  @Override
  public void run(@NonNull ApplicationArguments arguments) {
    reportService.generateAndWrite("relatorio-diario", "Vendas do dia: 42 pedidos");
  }
}