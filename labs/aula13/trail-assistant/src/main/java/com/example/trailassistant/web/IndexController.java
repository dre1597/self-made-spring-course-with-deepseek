package com.example.trailassistant.web;

import com.example.trailassistant.rag.TrailGuideIndexer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class IndexController {

  private final TrailGuideIndexer indexer;

  public IndexController(TrailGuideIndexer indexer) {
    this.indexer = indexer;
  }

  @PostMapping("/index")
  public ResponseEntity<Void> index() {
    indexer.index();
    return ResponseEntity.noContent().build();
  }
}