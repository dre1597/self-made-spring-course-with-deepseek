package com.example.learningmonolith.catalog;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.learningmonolith.catalog.events.CoursePublished;

@Service
public class CatalogService {

  private final ApplicationEventPublisher eventPublisher;

  public CatalogService(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public CoursePublished publish(Long courseId, String title) {
    CoursePublished event = new CoursePublished(courseId, title);
    eventPublisher.publishEvent(event);
    return event;
  }
}