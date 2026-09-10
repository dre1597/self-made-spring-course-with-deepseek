package com.example.tasks.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record AppUsersProperties(List<User> users) {

  public record User(String username, String password, List<String> roles) {
  }
}