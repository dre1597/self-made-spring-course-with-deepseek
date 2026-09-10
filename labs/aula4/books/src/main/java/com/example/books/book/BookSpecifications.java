package com.example.books.book;

import org.springframework.data.jpa.domain.Specification;

public final class BookSpecifications {

  private BookSpecifications() {
  }

  public static Specification<Book> byAuthor(String author) {
    return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("author"), author);
  }

  public static Specification<Book> titleContains(String fragment) {
    return (root, query, criteriaBuilder) -> criteriaBuilder
        .like(criteriaBuilder.lower(root.get("title")), "%" + fragment.toLowerCase() + "%");
  }
}