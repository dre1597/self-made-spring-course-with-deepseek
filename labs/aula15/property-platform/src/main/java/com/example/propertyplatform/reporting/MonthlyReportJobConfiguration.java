package com.example.propertyplatform.reporting;

import java.sql.Timestamp;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MonthlyReportJobConfiguration {

  @Bean
  Job monthlyReportJob(JobRepository jobRepository, Step monthlyReportStep) {
    return new JobBuilder("monthlyReportJob", jobRepository)
        .start(monthlyReportStep)
        .build();
  }

  @Bean
  Step monthlyReportStep(
      JobRepository jobRepository,
      PlatformTransactionManager transactionManager,
      JdbcPagingItemReader<MonthlyVisitRow> monthlyVisitReader,
      ItemProcessor<MonthlyVisitRow, MonthlyReportLine> processor,
      ItemWriter<MonthlyReportLine> writer) {
    return new StepBuilder("monthlyReportStep", jobRepository)
        .<MonthlyVisitRow, MonthlyReportLine>chunk(50)
        .transactionManager(transactionManager)
        .reader(monthlyVisitReader)
        .processor(processor)
        .writer(writer)
        .faultTolerant()
        .skip(IllegalStateException.class)
        .skipLimit(10)
        .build();
  }

  @Bean
  @StepScope
  JdbcPagingItemReader<MonthlyVisitRow> monthlyVisitReader(
      DataSource dataSource,
      @Value("#{jobParameters['month']}") String month) throws Exception {
    YearMonth yearMonth = YearMonth.parse(month);
    Timestamp start = Timestamp.from(yearMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    Timestamp end = Timestamp.from(yearMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
    return new JdbcPagingItemReaderBuilder<MonthlyVisitRow>()
        .name("monthlyVisitReader")
        .dataSource(dataSource)
        .selectClause("SELECT id, property_id, visitor_name, scheduled_at")
        .fromClause("FROM visits")
        .whereClause("WHERE scheduled_at >= :start AND scheduled_at < :end")
        .sortKeys(Map.of("id", Order.ASCENDING))
        .parameterValues(Map.of("start", start, "end", end))
        .rowMapper(new DataClassRowMapper<>(MonthlyVisitRow.class))
        .pageSize(100)
        .build();
  }
}