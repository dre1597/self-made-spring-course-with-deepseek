package com.example.learningcatalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseCatalogController {

  @GetMapping
  public List<CourseSummary> list() {
    return List.of(
        new CourseSummary(1L, "Arquitetura de sistemas"));
  }

  public record CourseSummary(Long id, String title) {
  }
}