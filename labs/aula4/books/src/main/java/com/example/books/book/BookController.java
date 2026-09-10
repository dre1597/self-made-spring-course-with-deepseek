package com.example.books.book;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/books")
public class BookController {

  private final BookService bookService;
  private final BookCatalogService catalogService;
  private final BookReportRepository reportRepository;

  public BookController(BookService bookService,
                        BookCatalogService catalogService,
                        BookReportRepository reportRepository) {
    this.bookService = bookService;
    this.catalogService = catalogService;
    this.reportRepository = reportRepository;
  }

  @GetMapping
  public Page<Book> findAll(Pageable pageable) {
    return bookService.findAll(pageable);
  }

  @GetMapping("/latest")
  public Book findLatest() {
    return bookService.findLatest();
  }

  @GetMapping("/by-author/{author}")
  public List<Book> findByAuthor(@PathVariable String author) {
    return bookService.findByAuthor(author);
  }

  @GetMapping("/by-title/{fragment}")
  public List<Book> findByTitleContaining(@PathVariable String fragment) {
    return bookService.findByTitleContaining(fragment);
  }

  @GetMapping("/search")
  public List<Book> search(@RequestParam(required = false) String author,
                           @RequestParam(required = false) String fragment) {
    return bookService.search(author, fragment);
  }

  @GetMapping("/summary")
  public List<BookSummary> summaries(@RequestParam String author) {
    return bookService.findSummariesByAuthor(author);
  }

  @GetMapping("/report/per-author")
  public List<BookReportRepository.BooksPerAuthor> booksPerAuthor() {
    return reportRepository.countBooksPerAuthor();
  }

  @GetMapping("/{id}")
  public Book findById(@PathVariable Long id) {
    return catalogService.findById(id);
  }

  @PostMapping
  public Book create(@RequestBody CreateBookRequest request) {
    return bookService.create(request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    bookService.delete(id);
  }
}