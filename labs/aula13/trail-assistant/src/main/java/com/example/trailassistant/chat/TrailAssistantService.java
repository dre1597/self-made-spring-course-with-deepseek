package com.example.trailassistant.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class TrailAssistantService {

  private final ChatClient chatClient;

  public TrailAssistantService(ChatClient.Builder builder, TrailTools tools) {
    this.chatClient = builder
        .defaultSystem("Você é um guia de trilhas. Use a ferramenta quando precisar consultar o catálogo.")
        .defaultTools(tools)
        .build();
  }

  public String ask(String question) {
    return chatClient.prompt()
        .user(question)
        .call()
        .content();
  }
}