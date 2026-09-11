package com.example.inventory.config;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfiguration {

  @Bean
  ThreadPoolTaskExecutor taskExecutor(TaskDecorator taskDecorator) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setTaskDecorator(taskDecorator);
    executor.initialize();
    return executor;
  }

  @Bean
  TaskDecorator taskDecorator() {
    return runnable -> {
      Map<String, String> context = MDC.getCopyOfContextMap();
      return () -> {
        if (context != null) {
          MDC.setContextMap(context);
        }
        try {
          runnable.run();
        } finally {
          MDC.clear();
        }
      };
    };
  }
}