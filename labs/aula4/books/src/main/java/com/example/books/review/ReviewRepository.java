package com.example.books.review;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

public interface ReviewRepository extends CrudRepository<Review, Long> {

  List<Review> findByBookId(Long bookId);
}