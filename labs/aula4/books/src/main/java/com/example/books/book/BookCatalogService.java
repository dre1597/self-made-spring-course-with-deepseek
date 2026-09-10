package com.example.books.book;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookCatalogService {

  private final BookRepository repository;

  public BookCatalogService(BookRepository repository) {
    this.repository = repository;
  }

  @Cacheable("books")
  @Transactional(readOnly = true)
  public Book findById(Long id) {
    return repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
  }
}