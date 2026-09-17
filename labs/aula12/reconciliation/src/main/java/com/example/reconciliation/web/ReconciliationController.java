package com.example.reconciliation.web;

import com.example.reconciliation.job.ReconciliationRunner;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

  private final ReconciliationRunner runner;

  public ReconciliationController(ReconciliationRunner runner) {
    this.runner = runner;
  }

  @PostMapping("/runs/{runId}")
  public ResponseEntity<JobStatus> run(@PathVariable String runId) throws JobExecutionException {
    JobExecution execution = runner.run(runId);
    return ResponseEntity.ok(JobStatus.from(execution));
  }

  public record JobStatus(Long executionId, String status) {

    static JobStatus from(JobExecution execution) {
      return new JobStatus(execution.getId(), execution.getStatus().name());
    }
  }
}