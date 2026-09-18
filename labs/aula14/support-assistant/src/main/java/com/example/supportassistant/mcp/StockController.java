package com.example.supportassistant.mcp;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class StockController {

  private final StockAssistantService stockAssistantService;

  public StockController(StockAssistantService stockAssistantService) {
    this.stockAssistantService = stockAssistantService;
  }

  @PostMapping("/stock")
  public StockAnswer stock(@RequestBody StockRequest request) {
    return new StockAnswer(stockAssistantService.askStock(request.message()));
  }

  public record StockRequest(String message) {
  }

  public record StockAnswer(String answer) {
  }
}