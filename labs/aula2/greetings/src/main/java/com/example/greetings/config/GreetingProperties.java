package com.example.greetings.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "greeting")
public record GreetingProperties(String defaultLanguage, String suffix) {
}