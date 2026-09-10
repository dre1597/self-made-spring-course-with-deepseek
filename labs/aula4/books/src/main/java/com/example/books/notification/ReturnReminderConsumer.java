package com.example.books.notification;

import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ReturnReminderConsumer {

  private static final Logger logger = LoggerFactory.getLogger(ReturnReminderConsumer.class);

  private final RBlockingQueue<Long> queue;

  public ReturnReminderConsumer(RedissonClient redisson) {
    this.queue = redisson.getBlockingQueue("return-reminders");
  }

  @PostConstruct
  void start() {
    Thread.ofVirtual().start(this::consume);
  }

  private void consume() {
    while (true) {
      try {
        Long loanId = queue.take();
        notifyMember(loanId);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private void notifyMember(Long loanId) {
    logger.info("Lembrete de devolução enviado pro empréstimo {}", loanId);
  }
}