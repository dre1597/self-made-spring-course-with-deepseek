package com.example.firstproject.greeting;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class GreetingFormatter {
  public String formatWithSuffix(String message, @Nullable String suffix) {
    return suffix == null ? message : message + " " + suffix;
  }
}