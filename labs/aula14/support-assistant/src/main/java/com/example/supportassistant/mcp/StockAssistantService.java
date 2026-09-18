package com.example.supportassistant.mcp;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.stereotype.Service;

@Service
public class StockAssistantService {

  private final ChatClient chatClient;

  private final SyncMcpToolCallbackProvider mcpToolCallbacks;

  public StockAssistantService(ChatClient chatClient, SyncMcpToolCallbackProvider mcpToolCallbacks) {
    this.chatClient = chatClient;
    this.mcpToolCallbacks = mcpToolCallbacks;
  }

  public String askStock(String message) {
    return chatClient.prompt()
        .user(message)
        .tools(mcpToolCallbacks)
        .call()
        .content();
  }
}