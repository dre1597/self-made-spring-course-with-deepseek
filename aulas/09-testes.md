# Aula 09 — Testes

Objetivo: testar com `@SpringBootTest`, slices e Testcontainers, no JUnit 6.

## Dependências

```kotlin
dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}
```

Cada slice tem seu starter de teste. `spring-boot-starter-test` traz JUnit 6, AssertJ e Mockito.

## JUnit 6

O Boot 4 roda sobre JUnit 6, que saiu em setembro de 2025. A migração do JUnit 5 é suave: os imports de `org.junit.jupiter.api.*` e os asserts do AssertJ são os mesmos. O que mudou foi a remoção de APIs deprecadas há mais de dois anos.

## @SpringBootTest

Sobe o contexto completo, como a aplicação real. Bom pra testar a integração entre as camadas:

```java
package com.example.books.book;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookServiceTest {

    @Autowired
    BookService bookService;

    @Test
    void createsBook() {
        Book book = bookService.create(new CreateBookRequest("Domain-Driven Design", "Eric Evans"));
        assertThat(book.id()).isNotNull();
    }
}
```

Contexto completo é lento; use pra fluxos que atravessam camadas. Pra camada isolada, use slice.

## @WebMvcTest

Testa só o controller, sem subir banco nem serviço. Dependências viram mocks com `@MockitoBean`:

```java
package com.example.books.book;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(BookController.class)
class BookControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    BookService bookService;

    @Test
    void listsBooks() {
        Mockito.when(bookService.findAll())
                .thenReturn(List.of(new Book(1L, "Domain-Driven Design", "Eric Evans", Instant.now())));

        assertThat(mvc.get().uri("/api/books"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].title")
                .isEqualTo("Domain-Driven Design");
    }
}
```

No Boot 4 os slices mudaram de pacote: `@WebMvcTest` agora é `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, e `@MockBean` virou `@MockitoBean` (`org.springframework.test.context.bean.override.mockito`). O `MockMvcTester` é o estilo AssertJ de testar MVC, no lugar do `MockMvc` com matchers encadeados.

## @DataJpaTest

Testa só o repository, com banco em memória e rollback automático:

```java
package com.example.books.book;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BookRepositoryTest {

    @Autowired
    BookRepository repository;

    @Test
    void findsByAuthor() {
        repository.save(new Book("Domain-Driven Design", "Eric Evans"));

        assertThat(repository.findByAuthor("Eric Evans")).hasSize(1);
    }
}
```

`@DataJpaTest` fica em `org.springframework.boot.data.jpa.test.autoconfigure`. Cada teste roda numa transação que desfaz no final.

## Segurança

Pra rodar como um usuário específico, `@WithMockUser`:

```java
package com.example.books.book;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(BookController.class)
class BookControllerSecurityTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAccessesEndpoint() {
        assertThat(mvc.delete().uri("/api/books/1")).doesNotHaveFailed();
    }

    @Test
    void anonymousIsRejected() {
        assertThat(mvc.delete().uri("/api/books/1")).hasStatus3xxRedirection();
    }
}
```

O `@WithMockUser` injeta um `SecurityContext` antes do teste. Sem usuário, a requisição é rejeitada.

## Eventos

Pra verificar que um evento foi publicado, capture com `@RecordApplicationEvents`:

```java
package com.example.books.book;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.RecordApplicationEvents;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RecordApplicationEvents
class BookServiceEventsTest {

    @Autowired
    BookService bookService;

    @Autowired
    ApplicationEvents events;

    @Test
    void publishesBookCreated() {
        bookService.create(new CreateBookRequest("Domain-Driven Design", "Eric Evans"));

        assertThat(events.stream(BookCreated.class)).hasSize(1);
    }
}
```

`@RecordApplicationEvents` grava os eventos publicados durante o teste, e `ApplicationEvents` te deixa consultar por tipo.

## Testcontainers

Pra testar contra o banco real em container, em vez do H2 em memória:

```java
package com.example.books.book;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class BookRepositoryPostgresTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    BookRepository repository;

    @Test
    void savesAndFinds() {
        repository.save(new Book("Domain-Driven Design", "Eric Evans"));

        assertThat(repository.findAll()).hasSize(1);
    }
}
```

`@ServiceConnection` liga o container ao `DataSource` automaticamente, sem propriedades manuais. O teste roda contra Postgres de verdade, pegando diferenças que o H2 esconde.

## Estrutura

```
src/test/java/com/example/books/
├── book/
│   ├── BookServiceTest.java
│   ├── BookControllerTest.java
│   ├── BookControllerSecurityTest.java
│   ├── BookRepositoryTest.java
│   ├── BookServiceEventsTest.java
│   └── BookRepositoryPostgresTest.java
```
