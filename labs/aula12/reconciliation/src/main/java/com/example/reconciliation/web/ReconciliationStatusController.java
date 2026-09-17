package com.example.reconciliation.web;

import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliation/runs")
public class ReconciliationStatusController {

  private final JobRepository jobRepository;

  public ReconciliationStatusController(JobRepository jobRepository) {
    this.jobRepository = jobRepository;
  }

  @GetMapping("/{executionId}")
  public ResponseEntity<String> status(@PathVariable Long executionId) {
    JobExecution execution = jobRepository.getJobExecution(executionId);
    if (execution == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(execution.getStatus().name());
  }
}