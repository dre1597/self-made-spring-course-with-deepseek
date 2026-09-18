package com.example.trailassistant.rag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
public class TrailGuideIndexer {

  private final VectorStore vectorStore;

  public TrailGuideIndexer(VectorStore vectorStore) {
    this.vectorStore = vectorStore;
  }

  public void index() {
    var splitter = TokenTextSplitter.builder()
        .withChunkSize(800)
        .withMinChunkSizeChars(350)
        .withKeepSeparator(true)
        .build();

    for (var guide : guides()) {
      var reader = new TextReader(guide);
      reader.getCustomMetadata().put("source", Objects.requireNonNull(guide.getFilename()));
      List<Document> chunks = splitter.apply(reader.get());
      vectorStore.add(chunks);
    }
  }

  private Resource[] guides() {
    try {
      return new PathMatchingResourcePatternResolver()
          .getResources("classpath:data/guides/*.md");
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}