package com.example.learningenrollment;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

  private final CourseRecommendationService recommendationService;

  public RecommendationController(CourseRecommendationService recommendationService) {
    this.recommendationService = recommendationService;
  }

  @GetMapping
  public List<CourseRecommendationService.CourseSummary> list() {
    return recommendationService.findFeaturedCourses();
  }
}