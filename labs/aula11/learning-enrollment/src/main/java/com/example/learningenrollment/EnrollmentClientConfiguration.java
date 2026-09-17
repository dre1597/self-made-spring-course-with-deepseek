package com.example.learningenrollment;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class EnrollmentClientConfiguration {

  @Bean
  @Primary
  RestClient.Builder directRestClientBuilder() {
    return RestClient.builder();
  }

  @Bean
  @LoadBalanced
  RestClient.Builder loadBalancedRestClientBuilder() {
    return RestClient.builder();
  }
}