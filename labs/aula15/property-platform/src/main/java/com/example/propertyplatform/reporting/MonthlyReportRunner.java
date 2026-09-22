package com.example.propertyplatform.reporting;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Service;

@Service
public class MonthlyReportRunner {

  private final JobOperator jobOperator;

  private final Job monthlyReportJob;

  public MonthlyReportRunner(JobOperator jobOperator, Job monthlyReportJob) {
    this.jobOperator = jobOperator;
    this.monthlyReportJob = monthlyReportJob;
  }

  public JobExecution run(String month) throws JobExecutionException {
    return jobOperator.start(monthlyReportJob, new JobParametersBuilder()
        .addString("month", month)
        .toJobParameters());
  }
}