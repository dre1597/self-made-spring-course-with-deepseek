package com.example.propertyplatform.listing;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

  private final PropertyService propertyService;

  public PropertyController(PropertyService propertyService) {
    this.propertyService = propertyService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('AGENT')")
  public PropertyResponse create(@Valid @RequestBody CreatePropertyRequest request) {
    Property property = propertyService.create(request.title(), request.city(), request.monthlyRent());
    return toResponse(property);
  }

  @GetMapping
  public List<PropertyResponse> findAll() {
    return propertyService.findAll().stream()
        .map(PropertyController::toResponse)
        .toList();
  }

  private static PropertyResponse toResponse(Property property) {
    return new PropertyResponse(
        property.getId(),
        property.getTitle(),
        property.getCity(),
        property.getMonthlyRent(),
        property.getStatus().name());
  }
}