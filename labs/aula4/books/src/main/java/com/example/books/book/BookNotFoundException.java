package com.example.books.book;

public class BookNotFoundException extends RuntimeException {

  public BookNotFoundException() {
    super("Livro não encontrado");
  }

  public BookNotFoundException(Long id) {
    super("Livro " + id + " não encontrado");
  }
}