package com.example.propertyplatform.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssistantConfiguration {

  @Bean
  ChatClient propertyChatClient(ChatClient.Builder builder) {
    return builder
        .defaultSystem("""
                        Você é o assistente da imobiliária Morada Certa.
                        Responda em português, de forma curta e direta.
                        Use as ferramentas quando precisar de dados de imóveis.
                        Quando houver contexto recuperado, baseie a resposta nele.
                        Se não souber, diga que não sabe.
                        """)
        .build();
  }
}