package com.example.supportassistant.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssistantConfiguration {

  @Bean
  ChatClient supportChatClient(ChatClient.Builder builder) {
    return builder
        .defaultSystem("""
                        Você é o assistente da livraria Página Viva.
                        Responda em português, de forma curta e direta.
                        Use as ferramentas disponíveis quando precisar de dados internos.
                        Quando houver contexto recuperado, baseie a resposta nele.
                        Se não souber a resposta, diga que não sabe.
                        """)
        .build();
  }
}