package com.example.books.book;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

  private final BookRepository repository;
  private final BookAuditService auditService;

  public BookService(BookRepository repository, BookAuditService auditService) {
    this.repository = repository;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public Page<Book> findAll(Pageable pageable) {
    return repository.findAll(pageable);
  }

  @Transactional(readOnly = true)
  public Book findLatest() {
    return repository.findFirstByOrderByCreatedAtDesc()
        .orElseThrow(BookNotFoundException::new);
  }

  @Transactional(readOnly = true)
  public Book findById(Long id) {
    return repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
  }

  @Transactional(readOnly = true)
  public List<Book> findByAuthor(String author) {
    return repository.findByAuthor(author);
  }

  @Transactional(readOnly = true)
  public List<Book> findByTitleContaining(String fragment) {
    return repository.findByTitleContainingIgnoreCase(fragment);
  }

  @Transactional(readOnly = true)
  public List<Book> search(String author, String fragment) {
    Specification<Book> specification = Specification.unrestricted();
    if (author != null && !author.isBlank()) {
      specification = specification.and(BookSpecifications.byAuthor(author));
    }
    if (fragment != null && !fragment.isBlank()) {
      specification = specification.and(BookSpecifications.titleContains(fragment));
    }
    return repository.findAll(specification);
  }

  @Transactional(readOnly = true)
  public List<BookSummary> findSummariesByAuthor(String author) {
    return repository.findSummariesByAuthor(author);
  }

  @Transactional
  public Book create(CreateBookRequest request) {
    return repository.save(new Book(request.title(), request.author()));
  }

  @Transactional
  public void delete(Long id) {
    repository.deleteById(id);
    auditService.recordDeletion(id);
  }
}