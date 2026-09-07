package com.example.greetings.greeting;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class PortugueseGreetingService implements GreetingService {

  @Override
  public String greet(String name) {
    return "Olá, " + name + "!";
  }
}