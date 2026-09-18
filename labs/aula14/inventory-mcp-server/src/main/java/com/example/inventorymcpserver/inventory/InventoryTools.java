package com.example.inventorymcpserver.inventory;

import java.util.Map;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;

@Service
public class InventoryTools {

  private static final Map<String, Integer> STOCK_BY_TITLE = Map.of(
      "O Cortiço", 4,
      "Dom Casmurro", 0,
      "Grande Sertão: Veredas", 7);

  @McpTool(description = "Consulta a quantidade em estoque de um livro pelo título")
  public String stockByTitle(
      @McpToolParam(description = "Título exato do livro", required = true) String title) {
    Integer quantity = STOCK_BY_TITLE.get(title);
    if (quantity == null) {
      return "Livro não encontrado no estoque: " + title;
    }
    return "Estoque de \"" + title + "\": " + quantity + " unidades.";
  }
}