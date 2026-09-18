package com.example.supportassistant.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {

  private final ChatClient chatClient;

  private final MessageChatMemoryAdvisor chatMemoryAdvisor;

  private final QuestionAnswerAdvisor questionAnswerAdvisor;

  public AssistantService(ChatClient chatClient, ChatMemory chatMemory, VectorStore supportVectorStore) {
    this.chatClient = chatClient;
    this.chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
    this.questionAnswerAdvisor = QuestionAnswerAdvisor.builder(supportVectorStore).build();
  }

  public String chat(String conversationId, String message) {
    return chatClient.prompt()
        .user(message)
        .advisors(chatMemoryAdvisor)
        .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
        .call()
        .content();
  }

  public String askFaq(String question) {
    return chatClient.prompt()
        .user(question)
        .advisors(questionAnswerAdvisor)
        .call()
        .content();
  }
}