package com.example.propertyplatform.notification;

import com.example.propertyplatform.visit.events.VisitScheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class VisitNotificationListener {

  private static final Logger logger = LoggerFactory.getLogger(VisitNotificationListener.class);

  @ApplicationModuleListener
  public void on(VisitScheduled event) {
    logger.info("Visita {} confirmada para {} no imóvel {}",
        event.visitId(), event.visitorName(), event.propertyId());
  }
}