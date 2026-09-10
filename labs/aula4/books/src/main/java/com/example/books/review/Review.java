package com.example.books.review;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("reviews")
public class Review {

  @Id
  private Long id;

  private Long bookId;

  private String comment;

  public Review(Long bookId, String comment) {
    this.bookId = bookId;
    this.comment = comment;
  }

  public Long getId() {
    return id;
  }

  public Long getBookId() {
    return bookId;
  }

  public String getComment() {
    return comment;
  }
}