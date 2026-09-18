package com.example.supportassistant.assistant;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

  private final AssistantService assistantService;

  public AssistantController(AssistantService assistantService) {
    this.assistantService = assistantService;
  }

  @PostMapping("/chat")
  public AssistantAnswer chat(@RequestBody ChatRequest request) {
    return new AssistantAnswer(assistantService.chat(request.conversationId(), request.message()));
  }

  @PostMapping("/faq")
  public AssistantAnswer faq(@RequestBody FaqRequest request) {
    return new AssistantAnswer(assistantService.askFaq(request.question()));
  }

  public record ChatRequest(String conversationId, String message) {
  }

  public record FaqRequest(String question) {
  }

  public record AssistantAnswer(String answer) {
  }
}