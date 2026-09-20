package com.example.propertyplatform.assistant;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

  private final VectorStore policyVectorStore;

  private final PolicyIndexer policyIndexer;

  public KnowledgeService(VectorStore policyVectorStore, PolicyIndexer policyIndexer) {
    this.policyVectorStore = policyVectorStore;
    this.policyIndexer = policyIndexer;
  }

  public List<KnowledgeMatch> search(String question) {
    policyIndexer.ensureIndexed();
    SearchRequest searchRequest = SearchRequest.builder()
        .query(question)
        .topK(3)
        .build();
    return policyVectorStore.similaritySearch(searchRequest).stream()
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