package com.example.propertyplatform.listing;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyService implements PropertyCatalog {

  private final PropertyRepository repository;

  public PropertyService(PropertyRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Property create(String title, String city, BigDecimal monthlyRent) {
    return repository.save(new Property(title, city, monthlyRent));
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