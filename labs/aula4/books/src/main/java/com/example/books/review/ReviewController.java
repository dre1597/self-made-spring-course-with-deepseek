package com.example.books.review;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/books/{bookId}/reviews")
public class ReviewController {

  private final ReviewRepository repository;

  public ReviewController(ReviewRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  public List<Review> findByBook(@PathVariable Long bookId) {
    return repository.findByBookId(bookId);
  }

  @PostMapping
  public Review create(@PathVariable Long bookId, @RequestBody CreateReviewRequest request) {
    return repository.save(new Review(bookId, request.comment()));
  }
}