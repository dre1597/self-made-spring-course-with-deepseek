package com.example.oidc.quote;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

  private final List<String> quotes = List.of(
      "A persistência é o caminho do êxito.",
      "O sucesso é a soma de pequenos esforços repetidos dia após dia.");

  @GetMapping
  public List<String> findAll() {
    return quotes;
  }
}