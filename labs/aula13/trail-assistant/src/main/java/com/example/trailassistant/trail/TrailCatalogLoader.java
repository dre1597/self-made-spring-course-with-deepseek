package com.example.trailassistant.trail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class TrailCatalogLoader implements ApplicationRunner {

  private final TrailRepository trailRepository;

  public TrailCatalogLoader(TrailRepository trailRepository) {
    this.trailRepository = trailRepository;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (trailRepository.count() > 0) {
      return;
    }
    trailRepository.saveAll(readCatalog());
  }

  private List<Trail> readCatalog() {
    var resource = new ClassPathResource("data/trails.csv");
    try (var lines = resource.getContentAsString(StandardCharsets.UTF_8).lines()) {
      return lines.skip(1)
          .filter(line -> !line.isBlank())
          .map(this::toTrail)
          .toList();
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private Trail toTrail(String line) {
    var columns = line.split(",");
    var distanceKm = unquote(columns[3]);
    return new Trail(
        unquote(columns[0]),
        unquote(columns[1]),
        unquote(columns[2]),
        distanceKm.isBlank() ? null : Double.valueOf(distanceKm));
  }

  private String unquote(String value) {
    return value.replace("\"", "");
  }
}