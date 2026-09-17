package com.example.reconciliation.job;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Service;

@Service
public class ReconciliationRunner {

  private final JobOperator jobOperator;
  private final Job reconciliationJob;

  public ReconciliationRunner(JobOperator jobOperator, Job reconciliationJob) {
    this.jobOperator = jobOperator;
    this.reconciliationJob = reconciliationJob;
  }

  public JobExecution run(String runId) throws JobExecutionException {
    return jobOperator.start(reconciliationJob, new JobParametersBuilder()
        .addString("runId", runId)
        .toJobParameters());
  }
}