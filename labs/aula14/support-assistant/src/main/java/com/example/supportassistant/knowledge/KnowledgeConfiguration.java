package com.example.supportassistant.knowledge;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeConfiguration {

  @Bean
  SimpleVectorStore supportVectorStore(EmbeddingModel embeddingModel) {
    SimpleVectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
    vectorStore.add(List.of(
        Document.builder()
            .text("O frete é grátis para compras acima de R$ 150. Abaixo disso, custa R$ 12 para todo o Brasil.")
            .metadata("source", "politica-de-frete")
            .build(),
        Document.builder()
            .text("O prazo de entrega é de 3 a 7 dias úteis, dependendo da região.")
            .metadata("source", "prazo-de-entrega")
            .build(),
        Document.builder()
            .text("O cliente pode devolver um livro em até 30 dias após o recebimento, sem custo, desde que não haja sinais de uso.")
            .metadata("source", "politica-de-devolucao")
            .build(),
        Document.builder()
            .text("A Página Viva fica na Rua das Letras, 42, e abre de segunda a sábado, das 9h às 18h.")
            .metadata("source", "loja-fisica")
            .build()));
    return vectorStore;
  }
}