package com.example.trailassistant.trail;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrailRepository extends JpaRepository<Trail, Long> {

  List<Trail> findByRegionContainingIgnoreCaseAndDifficulty(String region, String difficulty);
}