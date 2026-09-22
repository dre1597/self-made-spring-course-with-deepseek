package com.example.propertyplatform.listing;

import java.math.BigDecimal;
import java.util.List;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyService implements PropertyCatalog {

  private final PropertyRepository repository;

  private final Counter createdListings;

  public PropertyService(PropertyRepository repository, MeterRegistry meterRegistry) {
    this.repository = repository;
    this.createdListings = Counter.builder("property.listings.created")
        .description("Anúncios criados")
        .register(meterRegistry);
  }

  @Transactional
  @Observed(name = "property.create-listing")
  public Property create(String title, String city, BigDecimal monthlyRent) {
    Property property = repository.save(new Property(title, city, monthlyRent));
    createdListings.increment();
    return property;
  }

  @Transactional(readOnly = true)
  public List<Property> findAll() {
    return repository.findAll();
  }

  @Override
  @Transactional(readOnly = true)
  public PropertySummary getById(Long id) {
    Property property = repository.findById(id)
        .orElseThrow(() -> new PropertyNotFoundException(id));
    return toSummary(property);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PropertySummary> findAvailable(String city, BigDecimal maxMonthlyRent) {
    List<Property> properties = (city == null || city.isBlank())
        ? repository.findByStatusAndMonthlyRentLessThanEqual(ListingStatus.ACTIVE, maxMonthlyRent)
        : repository.findByCityAndStatusAndMonthlyRentLessThanEqual(
        city, ListingStatus.ACTIVE, maxMonthlyRent);
    return properties.stream()
        .map(PropertyService::toSummary)
        .toList();
  }

  private static PropertySummary toSummary(Property property) {
    return new PropertySummary(
        property.getId(), property.getTitle(), property.getCity(), property.getMonthlyRent());
  }
}