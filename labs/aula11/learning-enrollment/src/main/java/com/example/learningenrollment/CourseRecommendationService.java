package com.example.learningenrollment;

import java.util.List;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CourseRecommendationService {

  private final RestClient restClient;

  public CourseRecommendationService(
      @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder) {
    this.restClient = builder.baseUrl("http://learning-catalog").build();
  }

  @CircuitBreaker(name = "catalog", fallbackMethod = "catalogFallback")
  public List<CourseSummary> findFeaturedCourses() {
    return restClient.get()
        .uri("/api/courses")
        .retrieve()
        .body(new org.springframework.core.ParameterizedTypeReference<>() {
        });
  }

  List<CourseSummary> catalogFallback(Throwable cause) {
    return List.of(new CourseSummary(0L, "Catálogo indisponível"));
  }

  public record CourseSummary(Long id, String title) {
  }
}