package com.example.books.book;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books/hateoas")
public class BookHypermediaController {

  private final BookService service;

  public BookHypermediaController(BookService service) {
    this.service = service;
  }

  @GetMapping("/{id}")
  public EntityModel<Book> findById(@PathVariable Long id) {
    Book book = service.findById(id);
    return EntityModel.of(book,
        linkTo(methodOn(BookHypermediaController.class).findById(id)).withSelfRel(),
        linkTo(methodOn(BookHypermediaController.class).delete(id)).withRel("delete"));
  }

  @GetMapping("/{id}/delete")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}