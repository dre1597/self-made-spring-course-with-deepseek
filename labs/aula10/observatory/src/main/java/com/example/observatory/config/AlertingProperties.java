package com.example.observatory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.alerting")
public record AlertingProperties(String providerApiKey) {
}