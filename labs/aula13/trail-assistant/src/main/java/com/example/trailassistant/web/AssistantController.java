package com.example.trailassistant.web;

import com.example.trailassistant.chat.TrailAssistantService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

  private final TrailAssistantService assistantService;

  public AssistantController(TrailAssistantService assistantService) {
    this.assistantService = assistantService;
  }

  @PostMapping("/ask")
  public Answer ask(@RequestBody AskRequest request) {
    return new Answer(assistantService.ask(request.question()));
  }

  public record AskRequest(String question) {
  }

  public record Answer(String answer) {
  }
}