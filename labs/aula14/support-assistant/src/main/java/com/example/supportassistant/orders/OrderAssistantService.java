package com.example.supportassistant.orders;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class OrderAssistantService {

  private final ChatClient chatClient;

  private final OrderTools orderTools;

  public OrderAssistantService(ChatClient chatClient, OrderTools orderTools) {
    this.chatClient = chatClient;
    this.orderTools = orderTools;
  }

  public String askOrders(String message) {
    return chatClient.prompt()
        .user(message)
        .tools(orderTools)
        .call()
        .content();
  }
}