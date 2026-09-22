package com.example.propertyplatform.reporting;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/monthly")
public class MonthlyReportController {

  private final MonthlyReportRunner runner;

  private final JobRepository jobRepository;

  public MonthlyReportController(MonthlyReportRunner runner, JobRepository jobRepository) {
    this.runner = runner;
    this.jobRepository = jobRepository;
  }

  @PostMapping("/{month}")
  public ResponseEntity<JobStatus> run(@PathVariable String month) throws JobExecutionException {
    JobExecution execution = runner.run(month);
    return ResponseEntity.ok(new JobStatus(execution.getId(), execution.getStatus().name()));
  }

  @GetMapping("/runs/{executionId}")
  public ResponseEntity<String> status(@PathVariable Long executionId) {
    JobExecution execution = jobRepository.getJobExecution(executionId);
    if (execution == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(execution.getStatus().name());
  }

  public record JobStatus(Long executionId, String status) {
  }
}