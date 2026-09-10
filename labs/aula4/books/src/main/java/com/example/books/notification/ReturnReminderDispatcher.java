package com.example.books.notification;

import org.redisson.api.RBlockingQueue;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ReturnReminderDispatcher {

  private static final long DISPATCH_INTERVAL_MILLIS = 1_000;

  private final RScoredSortedSet<Long> scheduledReminders;
  private final RBlockingQueue<Long> dueReminders;

  public ReturnReminderDispatcher(RedissonClient redisson) {
    this.scheduledReminders = redisson.getScoredSortedSet("return-reminder-agenda");
    this.dueReminders = redisson.getBlockingQueue("return-reminders");
  }

  @PostConstruct
  void start() {
    Thread.ofVirtual().start(this::dispatch);
  }

  private void dispatch() {
    while (true) {
      try {
        Double dueAt = scheduledReminders.firstScore();
        if (dueAt == null || dueAt > System.currentTimeMillis()) {
          Thread.sleep(DISPATCH_INTERVAL_MILLIS);
          continue;
        }
        Long loanId = scheduledReminders.pollFirst();
        if (loanId != null) {
          dueReminders.offer(loanId);
        }
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }
}