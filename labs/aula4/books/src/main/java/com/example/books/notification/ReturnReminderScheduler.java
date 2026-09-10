package com.example.books.notification;

import java.time.Duration;

import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
public class ReturnReminderScheduler {

  private final RScoredSortedSet<Long> scheduledReminders;

  public ReturnReminderScheduler(RedissonClient redisson) {
    this.scheduledReminders = redisson.getScoredSortedSet("return-reminder-agenda");
  }

  public void schedule(Long loanId, Duration delay) {
    double dueAt = System.currentTimeMillis() + delay.toMillis();
    scheduledReminders.add(dueAt, loanId);
  }
}