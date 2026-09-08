package com.example.books.book;

import java.time.Instant;

public record Book(Long id, String title, String author, Instant createdAt) {
}