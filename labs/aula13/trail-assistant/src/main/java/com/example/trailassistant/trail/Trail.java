package com.example.trailassistant.trail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trails")
public class Trail {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String region;

  @Column(nullable = false)
  private String difficulty;

  private Double distanceKm;

  protected Trail() {
  }

  public Trail(String name, String region, String difficulty, Double distanceKm) {
    this.name = name;
    this.region = region;
    this.difficulty = difficulty;
    this.distanceKm = distanceKm;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getRegion() {
    return region;
  }

  public String getDifficulty() {
    return difficulty;
  }

  public Double getDistanceKm() {
    return distanceKm;
  }
}