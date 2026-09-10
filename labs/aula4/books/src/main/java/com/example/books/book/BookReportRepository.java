package com.example.books.book;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BookReportRepository {

  private final JdbcClient jdbcClient;

  public BookReportRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public List<BooksPerAuthor> countBooksPerAuthor() {
    return jdbcClient.sql("""
                SELECT author, COUNT(*) AS total
                FROM books
                GROUP BY author
                ORDER BY total DESC
                """)
        .query((resultSet, rowNumber) -> new BooksPerAuthor(
            resultSet.getString("author"),
            resultSet.getLong("total")))
        .list();
  }

  public record BooksPerAuthor(String author, long total) {
  }
}