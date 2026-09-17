package com.example.learningstream;

import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class ProgressStreamController {

  @GetMapping(value = "/api/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<Progress> stream() {
    return Flux.interval(Duration.ofSeconds(1))
        .map(step -> new Progress(step, "processando"));
  }

  public record Progress(long step, String status) {
  }
}