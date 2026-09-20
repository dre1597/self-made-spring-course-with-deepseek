package com.example.propertyplatform.maintenance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

  List<MaintenanceRequest> findByPropertyId(Long propertyId);
}