package com.example.greetings.web;

import com.example.greetings.greeting.GreetingService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/greetings/en")
public class EnglishGreetingController {

  private final GreetingService greetingService;

  public EnglishGreetingController(@Qualifier("englishGreetingService") GreetingService greetingService) {
    this.greetingService = greetingService;
  }

  @GetMapping
  public String greet() {
    return greetingService.greet("world");
  }
}