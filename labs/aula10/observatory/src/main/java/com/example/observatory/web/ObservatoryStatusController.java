package com.example.observatory.web;

import java.time.Duration;

import com.example.observatory.config.ObservatoryProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ObservatoryStatusController {

  private final ObservatoryProperties properties;

  public ObservatoryStatusController(ObservatoryProperties properties) {
    this.properties = properties;
  }

  @GetMapping("/api/status")
  public Status status() {
    return new Status(properties.stationName(), properties.readingInterval());
  }

  public record Status(String stationName, Duration readingInterval) {
  }
}