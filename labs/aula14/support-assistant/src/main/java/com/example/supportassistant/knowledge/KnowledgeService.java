package com.example.supportassistant.knowledge;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

  private final VectorStore supportVectorStore;

  public KnowledgeService(VectorStore supportVectorStore) {
    this.supportVectorStore = supportVectorStore;
  }

  public List<KnowledgeMatch> search(String question) {
    SearchRequest searchRequest = SearchRequest.builder()
        .query(question)
        .topK(3)
        .build();
    return supportVectorStore.similaritySearch(searchRequest).stream()
        .map(KnowledgeService::toMatch)
        .toList();
  }

  private static KnowledgeMatch toMatch(Document document) {
    return new KnowledgeMatch(
        document.getText(),
        String.valueOf(document.getMetadata().get("source")),
        document.getScore());
  }
}