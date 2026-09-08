package com.example.books.config;

import com.example.books.book.PriceClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(types = PriceClient.class)
public class HttpServiceConfiguration {

  @Bean
  RestClientHttpServiceGroupConfigurer priceClientConfigurer() {
    return groups -> groups.forEachClient((group, builder) -> builder
        .baseUrl("http://localhost:9090")
        .build());
  }
}