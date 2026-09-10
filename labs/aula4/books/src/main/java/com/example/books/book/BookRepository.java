package com.example.books.book;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

  List<Book> findByAuthor(String author);

  List<Book> findByTitleContainingIgnoreCase(String fragment);

  Optional<Book> findFirstByOrderByCreatedAtDesc();

  List<BookSummary> findSummariesByAuthor(String author);
}