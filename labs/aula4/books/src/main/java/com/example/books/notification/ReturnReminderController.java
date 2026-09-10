package com.example.books.notification;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/return-reminders")
public class ReturnReminderController {

  private final ReturnReminderScheduler scheduler;

  public ReturnReminderController(ReturnReminderScheduler scheduler) {
    this.scheduler = scheduler;
  }

  @PostMapping("/{loanId}")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void schedule(@PathVariable Long loanId,
                       @RequestParam(defaultValue = "5") long delaySeconds) {
    scheduler.schedule(loanId, Duration.ofSeconds(delaySeconds));
  }
}