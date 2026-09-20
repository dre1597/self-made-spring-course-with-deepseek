package com.example.propertyplatform.assistant;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

@Component
public class PolicyIndexer {

  private final VectorStore vectorStore;

  private final Object lock = new Object();

  private volatile boolean indexed;

  public PolicyIndexer(VectorStore vectorStore) {
    this.vectorStore = vectorStore;
  }

  public void ensureIndexed() {
    if (indexed) {
      return;
    }
    synchronized (lock) {
      if (!indexed) {
        vectorStore.add(policyDocuments());
        indexed = true;
      }
    }
  }

  private static List<Document> policyDocuments() {
    return List.of(
        Document.builder()
            .text("A visita é acompanhada por um corretor e pode ser agendada com 24 horas de antecedência.")
            .metadata("source", "politica-de-visita")
            .build(),
        Document.builder()
            .text("O aluguel exige caução de três meses, devolvida ao fim do contrato sem danos ao imóvel.")
            .metadata("source", "politica-de-caucao")
            .build(),
        Document.builder()
            .text("Animais de pequeno porte são permitidos quando o condomínio autoriza.")
            .metadata("source", "politica-de-animais")
            .build(),
        Document.builder()
            .text("Pedidos de manutenção emergencial são atendidos em até 24 horas.")
            .metadata("source", "politica-de-manutencao")
            .build());
  }
}