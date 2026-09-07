package com.example.greetings.greeting;

import org.springframework.stereotype.Component;

@Component
public class GreetingFormatter {

  public String format(String message, String suffix) {
    if (suffix == null || suffix.isBlank()) {
      return message;
    }
    return message + " " + suffix;
  }
}