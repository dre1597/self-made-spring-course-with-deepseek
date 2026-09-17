package com.example.learningmonolith;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

@SuppressWarnings("unused")
class ArchitectureTest {

  @Test
  void modulesRespectTheirBoundaries() {
    ApplicationModules.of(LearningMonolithApplication.class).verify();
  }
}