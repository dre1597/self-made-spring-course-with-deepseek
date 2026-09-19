package com.example.propertyplatform.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(List<User> users) {

  public record User(String username, String password, List<String> roles) {
  }
}