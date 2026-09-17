package com.example.reconciliation.job;

import com.example.reconciliation.payment.PaymentCsvReader;
import com.example.reconciliation.payment.PaymentRow;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class ReconciliationJobConfiguration {

  @Bean
  Job reconciliationJob(JobRepository jobRepository, Step reconciliationStep) {
    return new JobBuilder("reconciliationJob", jobRepository)
        .start(reconciliationStep)
        .build();
  }

  @Bean
  Step reconciliationStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                          PaymentCsvReader paymentCsvReader,
                          ItemProcessor<PaymentRow, PaymentRow> processor,
                          ItemWriter<PaymentRow> writer) {
    FlatFileItemReader<PaymentRow> reader = paymentCsvReader.reader();
    return new StepBuilder("reconciliationStep", jobRepository)
        .<PaymentRow, PaymentRow>chunk(50)
        .transactionManager(transactionManager)
        .reader(reader)
        .processor(processor)
        .writer(writer)
        .faultTolerant()
        .skip(IllegalStateException.class)
        .skipLimit(10)
        .retry(TransientDataAccessException.class)
        .retryLimit(3)
        .build();
  }
}