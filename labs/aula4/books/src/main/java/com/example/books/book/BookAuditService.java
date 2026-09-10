package com.example.books.book;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookAuditService {

  private static final Logger logger = LoggerFactory.getLogger(BookAuditService.class);

  private final JdbcClient jdbcClient;

  public BookAuditService(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordDeletion(Long bookId) {
    jdbcClient.sql("INSERT INTO audit_log (book_id) VALUES (:bookId)")
        .param("bookId", bookId)
        .update();
    logger.info("Auditoria de exclusão gravada pro livro {}", bookId);
  }
}