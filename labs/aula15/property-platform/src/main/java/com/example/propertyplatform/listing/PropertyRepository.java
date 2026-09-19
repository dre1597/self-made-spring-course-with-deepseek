package com.example.propertyplatform.listing;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, Long> {

  List<Property> findByStatusAndMonthlyRentLessThanEqual(ListingStatus status, BigDecimal monthlyRent);

  List<Property> findByCityAndStatusAndMonthlyRentLessThanEqual(
      String city, ListingStatus status, BigDecimal monthlyRent);
}