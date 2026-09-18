package com.example.trailassistant.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class TrailAssistantService {

  private final ChatClient chatClient;

  public TrailAssistantService(ChatClient.Builder builder,
                               TrailTools tools,
                               VectorStore vectorStore) {
    var retrieval = RetrievalAugmentationAdvisor.builder()
        .documentRetriever(VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .topK(5)
            .similarityThreshold(0.2)
            .build())
        .build();

    this.chatClient = builder
        .defaultSystem("Responda usando os guias fornecidos. Se a informação não estiver no contexto, diga que não sabe.")
        .defaultTools(tools)
        .defaultAdvisors(retrieval)
        .build();
  }

  public String ask(String question) {
    return chatClient.prompt()
        .user(question)
        .call()
        .content();
  }
}