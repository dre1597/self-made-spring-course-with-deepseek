package com.example.greetings.web;

import java.time.Clock;
import java.time.Instant;

import com.example.greetings.config.GreetingProperties;
import com.example.greetings.greeting.GreetingFormatter;
import com.example.greetings.greeting.GreetingService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/greetings")
public class GreetingController {

  private final GreetingService greetingService;
  private final GreetingFormatter formatter;
  private final GreetingProperties properties;
  private final Clock clock;

  public GreetingController(GreetingService greetingService, GreetingFormatter formatter,
                            GreetingProperties properties, Clock clock) {
    this.greetingService = greetingService;
    this.formatter = formatter;
    this.properties = properties;
    this.clock = clock;
  }

  @GetMapping
  public GreetingResponse greet() {
    String message = formatter.format(greetingService.greet("mundo"), properties.suffix());
    return new GreetingResponse(message, Instant.now(clock));
  }

  public record GreetingResponse(String message, Instant timestamp) {
  }
}