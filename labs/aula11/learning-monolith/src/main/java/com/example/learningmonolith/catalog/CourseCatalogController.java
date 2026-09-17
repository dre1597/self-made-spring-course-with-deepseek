package com.example.learningmonolith.catalog;

import java.util.List;

import com.example.learningmonolith.catalog.events.CoursePublished;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseCatalogController {

  private final CatalogService catalogService;

  public CourseCatalogController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping
  public List<CourseSummary> list() {
    return List.of(new CourseSummary(1L, "Arquitetura de sistemas"));
  }

  @PostMapping
  public CoursePublished publish(@RequestBody PublishCourseRequest request) {
    return catalogService.publish(request.courseId(), request.title());
  }

  public record PublishCourseRequest(Long courseId, String title) {
  }

  public record CourseSummary(Long id, String title) {
  }
}