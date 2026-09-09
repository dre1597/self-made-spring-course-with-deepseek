# Aula 14 — gRPC

Objetivo: expor um serviço gRPC com Spring gRPC, definir o contrato em protobuf e consumir com um stub tipado.

## Por que gRPC

REST é JSON por HTTP, contrato solto. gRPC é contrato tipado em protobuf, com serialização binária e streaming nativo. Compensa em comunicação interna de alto volume, onde o overhead do JSON e o contrato implícito doem. Serviço a serviço, com protobuf, o cliente ganha um stub gerado: erro de tipo vira erro de compilação, não de runtime.

Spring gRPC 1.0.x roda com Boot 4.1. O `.proto` define o contrato e gera as classes Java; `@GrpcService` expõe o serviço; o cliente injeta um stub.

Base do projeto:

```kotlin
plugins {
    id("java")
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.9.5"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")
    implementation("org.springframework.boot:spring-boot-starter-grpc-client")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java"
        }
    }
}
```

O `grpc-server` expõe o serviço (seção Servidor); o `grpc-client` cria os channels e stubs (seção Cliente). O plugin protobuf compila o `.proto` em classes Java na build. O start.spring.io gera esse setup pronto quando você marca o starter gRPC server.

## O contrato

`src/main/proto/book.proto`:

```protobuf
syntax = "proto3";

package book;

option java_package = "com.example.books.book";

service BookService {
  rpc FindBook(FindBookRequest) returns (Book);
  rpc ListBooks(ListBooksRequest) returns (stream Book);
}

message FindBookRequest {
  int64 id = 1;
}

message ListBooksRequest {
}

message Book {
  int64 id = 1;
  string title = 2;
  string author = 3;
}
```

O `service` vira a classe base `BookServiceGrpc.BookServiceImplBase` e os stubs. `returns (stream Book)` é server-streaming: o servidor manda os livros um a um.

## Servidor

O `@GrpcService` registra o bean como serviço gRPC. Estenda a base gerada e implemente os métodos:

```java
package com.example.books.book;

import io.grpc.stub.StreamObserver;

import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class BookGrpcService extends BookServiceGrpc.BookServiceImplBase {

    private final BookService bookService;

    public BookGrpcService(BookService bookService) {
        this.bookService = bookService;
    }

    @Override
    public void findBook(FindBookRequest request, StreamObserver<Book> responseObserver) {
        Book book = bookService.findById(request.getId());
        responseObserver.onNext(book);
        responseObserver.onCompleted();
    }

    @Override
    public void listBooks(ListBooksRequest request, StreamObserver<Book> responseObserver) {
        for (Book book : bookService.findAll()) {
            responseObserver.onNext(book);
        }
        responseObserver.onCompleted();
    }
}
```

Unário (`findBook`) manda uma resposta e fecha com `onCompleted`. Streaming (`listBooks`) manda várias `onNext` antes de fechar. O servidor gRPC sobe numa porta própria (default `9090`), separada do HTTP.

## Cliente

O `GrpcChannelFactory` cria o channel e o stub bloqueante nasce dele:

```java
package com.example.orders.order;

import com.example.books.book.BookServiceGrpc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class BookGrpcClientConfiguration {

    @Bean
    BookServiceGrpc.BookServiceBlockingStub bookServiceStub(GrpcChannelFactory channels) {
        return BookServiceGrpc.newBlockingStub(channels.createChannel("0.0.0.0:9090"));
    }
}
```

O stub entra em qualquer bean e a chamada parece método local:

```java
package com.example.orders.order;

import com.example.books.book.Book;
import com.example.books.book.BookServiceGrpc;
import com.example.books.book.FindBookRequest;

import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final BookServiceGrpc.BookServiceBlockingStub bookService;

    public OrderService(BookServiceGrpc.BookServiceBlockingStub bookService) {
        this.bookService = bookService;
    }

    public Book resolveBook(long bookId) {
        return bookService.findBook(FindBookRequest.newBuilder().setId(bookId).build());
    }
}
```

O address vem inline no exemplo. Pra configurar por propriedade, `spring.grpc.client.*` mapeia channels nomeados, e o `@ImportGrpcClients` escaneia os stubs e registra os beans sozinho, no lugar do `@Bean` manual. Interceptores (auth, observabilidade, retry) entram via `GrpcChannelBuilderCustomizer`.

## gRPC vs REST

gRPC vence onde contrato forte e streaming importam, e onde o custo de manter o `.proto` compensa. REST vence em simplicidade, cache HTTP e integração com ferramentas do navegador. O ponto comum: ambos são chamadas entre processos; a escolha é sobre contrato e performance, não sobre "modernidade". O teste do serviço gRPC roda in-process com `@AutoConfigureTestGrpcTransport`, sem porta, no mesmo espírito dos slices de teste.

## Estrutura

```
src/main/proto/
└── book.proto
src/main/java/com/example/books/
├── BooksApplication.java
└── book/
    ├── BookGrpcService.java
    └── BookService.java
src/main/java/com/example/orders/
├── OrdersApplication.java
└── order/
    ├── BookGrpcClientConfiguration.java
    └── OrderService.java
```
