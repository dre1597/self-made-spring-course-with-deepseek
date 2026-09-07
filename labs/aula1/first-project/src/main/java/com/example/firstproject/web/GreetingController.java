package com.example.firstproject.web;

import java.time.Instant;

import com.example.firstproject.greeting.GreetingFormatter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/greetings")
public class GreetingController {

  private final GreetingFormatter formatter;

  public GreetingController(GreetingFormatter formatter) {
    this.formatter = formatter;
  }

  @GetMapping
  public GreetingResponse greet() {
    String message = formatter.formatWithSuffix("Hello, world!", null);
    return new GreetingResponse(message, Instant.now());
  }

  public record GreetingResponse(String message, Instant timestamp) {
  }
}
