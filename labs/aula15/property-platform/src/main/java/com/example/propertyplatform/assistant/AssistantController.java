package com.example.propertyplatform.assistant;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

  private final AssistantService assistantService;

  private final KnowledgeService knowledgeService;

  public AssistantController(AssistantService assistantService, KnowledgeService knowledgeService) {
    this.assistantService = assistantService;
    this.knowledgeService = knowledgeService;
  }

  @PostMapping("/chat")
  public AssistantAnswer chat(@RequestBody ChatRequest request) {
    return new AssistantAnswer(assistantService.chat(request.conversationId(), request.message()));
  }

  @PostMapping("/properties")
  public AssistantAnswer properties(@RequestBody AssistantRequest request) {
    return new AssistantAnswer(assistantService.findProperties(request.message()));
  }

  @PostMapping("/faq")
  public AssistantAnswer faq(@RequestBody FaqRequest request) {
    return new AssistantAnswer(assistantService.askFaq(request.question()));
  }

  @GetMapping("/search")
  public List<KnowledgeMatch> search(@RequestParam("question") String question) {
    return knowledgeService.search(question);
  }

  public record ChatRequest(String conversationId, String message) {
  }

  public record AssistantRequest(String message) {
  }

  public record FaqRequest(String question) {
  }

  public record AssistantAnswer(String answer) {
  }
}