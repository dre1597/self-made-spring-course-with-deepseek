package com.example.observatory.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.observatory")
public record ObservatoryProperties(String stationName, Duration readingInterval) {
}