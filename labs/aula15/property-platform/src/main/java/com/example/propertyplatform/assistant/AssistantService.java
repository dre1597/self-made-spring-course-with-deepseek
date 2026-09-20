package com.example.propertyplatform.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {

  private final ChatClient chatClient;

  private final PropertyTools propertyTools;

  private final PolicyIndexer policyIndexer;

  private final MessageChatMemoryAdvisor chatMemoryAdvisor;

  private final QuestionAnswerAdvisor questionAnswerAdvisor;

  public AssistantService(
      ChatClient chatClient,
      PropertyTools propertyTools,
      PolicyIndexer policyIndexer,
      ChatMemory chatMemory,
      VectorStore policyVectorStore) {
    this.chatClient = chatClient;
    this.propertyTools = propertyTools;
    this.policyIndexer = policyIndexer;
    this.chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
    this.questionAnswerAdvisor = QuestionAnswerAdvisor.builder(policyVectorStore).build();
  }

  public String chat(String conversationId, String message) {
    return chatClient.prompt()
        .user(message)
        .advisors(chatMemoryAdvisor)
        .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
        .call()
        .content();
  }

  public String findProperties(String message) {
    return chatClient.prompt()
        .user(message)
        .tools(propertyTools)
        .call()
        .content();
  }

  public String askFaq(String question) {
    policyIndexer.ensureIndexed();
    return chatClient.prompt()
        .user(question)
        .advisors(questionAnswerAdvisor)
        .call()
        .content();
  }
}