package com.example.propertyplatform.visit;

import java.util.List;

import com.example.propertyplatform.listing.PropertyCatalog;
import com.example.propertyplatform.visit.events.VisitScheduled;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitService {

  private final VisitRepository repository;

  private final PropertyCatalog propertyCatalog;

  private final ApplicationEventPublisher eventPublisher;

  private final Counter scheduledVisits;

  public VisitService(
      VisitRepository repository,
      PropertyCatalog propertyCatalog,
      ApplicationEventPublisher eventPublisher,
      MeterRegistry meterRegistry) {
    this.repository = repository;
    this.propertyCatalog = propertyCatalog;
    this.eventPublisher = eventPublisher;
    this.scheduledVisits = Counter.builder("visit.scheduled")
        .description("Visitas agendadas")
        .register(meterRegistry);
  }

  @Transactional
  public Visit schedule(ScheduleVisitRequest request) {
    propertyCatalog.getById(request.propertyId());
    Visit visit = repository.save(new Visit(
        request.propertyId(), request.visitorName(), request.scheduledAt()));
    eventPublisher.publishEvent(new VisitScheduled(
        visit.getId(), visit.getPropertyId(), visit.getVisitorName(), visit.getScheduledAt()));
    scheduledVisits.increment();
    return visit;
  }

  @Transactional(readOnly = true)
  public List<Visit> findAll() {
    return repository.findAll();
  }
}