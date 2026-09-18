package com.example.trailassistant.chat;

import java.util.List;

import com.example.trailassistant.trail.Trail;
import com.example.trailassistant.trail.TrailRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class TrailTools {

  private final TrailRepository trailRepository;

  public TrailTools(TrailRepository trailRepository) {
    this.trailRepository = trailRepository;
  }

  @Tool(description = "Busca trilhas no catálogo por região e dificuldade. A dificuldade aceita easy, moderate ou hard.")
  public List<Trail> findTrails(String region, String difficulty) {
    return trailRepository.findByRegionContainingIgnoreCaseAndDifficulty(region, difficulty);
  }
}