package com.example.greetings.greeting;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GreetingServiceLifecycle {

  private static final Logger logger = LoggerFactory.getLogger(GreetingServiceLifecycle.class);

  @PostConstruct
  void logStartup() {
    logger.info("GreetingServiceLifecycle inicializado");
  }

  @PreDestroy
  void logShutdown() {
    logger.info("GreetingServiceLifecycle encerrado");
  }
}