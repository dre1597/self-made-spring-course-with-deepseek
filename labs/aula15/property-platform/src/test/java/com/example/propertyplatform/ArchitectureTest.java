package com.example.propertyplatform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTest {

  @Test
  void modulesRespectTheirBoundaries() {
    ApplicationModules.of(PropertyPlatformApplication.class).verify();
  }
}