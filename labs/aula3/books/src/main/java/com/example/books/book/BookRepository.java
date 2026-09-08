package com.example.books.book;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

@Repository
public class BookRepository {

  private final Map<Long, Book> books = new ConcurrentHashMap<>();
  private final AtomicLong idSequence = new AtomicLong();

  public Book save(String title, String author) {
    long id = idSequence.incrementAndGet();
    Book book = new Book(id, title, author, Instant.now());
    books.put(id, book);
    return book;
  }

  public Optional<Book> findById(Long id) {
    return Optional.ofNullable(books.get(id));
  }

  public List<Book> findAll() {
    return List.copyOf(books.values());
  }

  public void delete(Long id) {
    books.remove(id);
  }
}