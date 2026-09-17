package com.example.learningmonolith.enrollment;

import com.example.learningmonolith.catalog.events.CoursePublished;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class CoursePublicationListener {

  private static final Logger logger = LoggerFactory.getLogger(CoursePublicationListener.class);

  @ApplicationModuleListener
  public void on(CoursePublished event) {
    logger.info("Curso {} disponível para matrícula", event.courseId());
  }
}