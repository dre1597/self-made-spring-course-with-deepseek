package com.example.propertyplatform.visit;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visits")
public class VisitController {

  private final VisitService visitService;

  public VisitController(VisitService visitService) {
    this.visitService = visitService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public VisitResponse schedule(@Valid @RequestBody ScheduleVisitRequest request) {
    Visit visit = visitService.schedule(request);
    return new VisitResponse(
        visit.getId(),
        visit.getPropertyId(),
        visit.getVisitorName(),
        visit.getScheduledAt(),
        visit.getStatus().name());
  }

  @GetMapping
  public List<VisitResponse> findAll() {
    return visitService.findAll().stream()
        .map(visit -> new VisitResponse(
            visit.getId(),
            visit.getPropertyId(),
            visit.getVisitorName(),
            visit.getScheduledAt(),
            visit.getStatus().name()))
        .toList();
  }
}