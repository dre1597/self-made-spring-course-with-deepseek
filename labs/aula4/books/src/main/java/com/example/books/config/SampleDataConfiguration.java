package com.example.books.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.books.book.Book;
import com.example.books.book.BookRepository;

@Configuration
public class SampleDataConfiguration {

  @Bean
  CommandLineRunner sampleBooks(BookRepository repository) {
    return args -> {
      repository.save(new Book("A Hora da Estrela", "Clarice Lispector"));
      repository.save(new Book("Água Viva", "Clarice Lispector"));
      repository.save(new Book("Dom Casmurro", "Machado de Assis"));
    };
  }
}