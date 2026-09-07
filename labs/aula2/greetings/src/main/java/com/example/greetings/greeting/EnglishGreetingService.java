package com.example.greetings.greeting;

import org.springframework.stereotype.Service;

@Service
public class EnglishGreetingService implements GreetingService {

  @Override
  public String greet(String name) {
    return "Hello, " + name + "!";
  }
}