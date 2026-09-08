package com.example.books.book;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class BookService {

  private final BookRepository repository;

  public BookService(BookRepository repository) {
    this.repository = repository;
  }

  public List<Book> findAll() {
    return repository.findAll();
  }

  public Book findById(Long id) {
    return repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
  }

  public Book create(CreateBookRequest request) {
    return repository.save(request.title(), request.author());
  }

  public void delete(Long id) {
    repository.delete(id);
  }
}