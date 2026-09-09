# Aula 03 — Web e REST

Objetivo: montar uma API REST com controllers, validação, tratamento de erro, clientes HTTP e versionamento.

## Controllers e routing

Base do projeto:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-webmvc")
```

O `webmvc` traz o MVC e o suporte a controllers REST, base da aula inteira.

O domínio é uma API de livros. Um record de livro:

```java
package com.example.books.book;

import java.time.Instant;

public record Book(Long id, String title, String author, Instant createdAt) {
}
```

Repositório em memória, só pra aula rodar sem banco:

```java
package com.example.books.book;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

@Repository
public class BookRepository {

    private final Map<Long, Book> books = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong();

    public Book save(String title, String author) {
        long id = idSequence.incrementAndGet();
        Book book = new Book(id, title, author, Instant.now());
        books.put(id, book);
        return book;
    }

    public Optional<Book> findById(Long id) {
        return Optional.ofNullable(books.get(id));
    }

    public List<Book> findAll() {
        return List.copyOf(books.values());
    }

    public void delete(Long id) {
        books.remove(id);
    }
}
```

Serviço:

```java
package com.example.books.book;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class BookService {

    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = repository;
    }

    public List<Book> findAll() {
        return repository.findAll();
    }

    public Book findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }

    public Book create(CreateBookRequest request) {
        return repository.save(request.title(), request.author());
    }

    public void delete(Long id) {
        repository.delete(id);
    }
}
```

Controller:

```java
package com.example.books.book;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    @GetMapping
    public List<Book> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public Book findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Book create(@Valid @RequestBody CreateBookRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
```

`@RestController` junta `@Controller` e `@ResponseBody`: o retorno de cada método é serializado direto no corpo da resposta. `@RequestMapping` no tipo define o prefixo; `@GetMapping` e `@PostMapping` compõem o caminho.

`@ResponseStatus` define o status da resposta. `POST` retorna `201 Created`; `DELETE` retorna `204 No Content`. Quando o status precisa depender do fluxo, use `ResponseEntity` em vez de `@ResponseStatus`.

## Bean Validation 3.1

Dependência da seção:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-validation")
```

No Boot 4 a validação saiu do starter web: se o código usa `jakarta.validation`, o starter precisa estar declarado.

O request de criação recebe as constraints:

```java
package com.example.books.book;

import jakarta.validation.constraints.NotBlank;

public record CreateBookRequest(@NotBlank String title, @NotBlank String author) {
}
```

`@Valid` no parâmetro do controller dispara a validação antes de entrar no método. Se `title` ou `author` vierem em branco, o Spring rejeita a requisição com `400` sem nunca chamar o serviço.

`@NotBlank` rejeita `null`, string vazia e só espaços. Outras constraints comuns: `@NotNull`, `@Size`, `@Positive`, `@Email`, `@Past`.

## Erro global com ProblemDetail

Erro de domínio:

```java
package com.example.books.book;

public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException(Long id) {
        super("Livro " + id + " não encontrado");
    }
}
```

Handler global:

```java
package com.example.books.book;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    ProblemDetail handleNotFound(BookNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Livro não encontrado");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Corpo inválido");
        problem.setTitle("Validação falhou");
        problem.setProperty("errors", exception.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList());
        return problem;
    }
}
```

`@RestControllerAdvice` intercepta exceções de todos os controllers. Cada `@ExceptionHandler` devolve um `ProblemDetail`, que o Spring serializa como o corpo `application/problem+json` da RFC 7807: `title`, `detail`, `status` e as propriedades extras.

Agora `GET /api/books/999` responde `404` com um `ProblemDetail` em vez de vazar stack trace.

## RestClient

Dependência da seção:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-restclient")
```

O `spring-boot-starter-restclient` traz o `RestClient` e as HTTP interfaces das seções abaixo. O Boot auto-configura um `RestClient.Builder`. Ele já vem com os conversores de mensagem e a fábrica de HTTP certa; você injeta e ajusta.

```java
package com.example.books.book;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class PriceService {

    private final RestClient restClient;

    public PriceService(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://localhost:9090").build();
    }

    public List<Price> findAll() {
        return restClient.get()
                .uri("/prices")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
    }

    public record Price(Long bookId, String currency, double amount) {
    }
}
```

`RestTemplate` está deprecated no Framework 7; `RestClient` é o substituto síncrono. Pra cenário reativo, `WebClient`.

## HTTP interfaces

O jeito declarativo troca o código imperativo por uma interface anotada, como faz o Spring Data com repositórios.

```java
package com.example.books.book;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/prices")
public interface PriceClient {

    @GetExchange("/{bookId}")
    PriceResponse findPrice(@PathVariable Long bookId);

    record PriceResponse(Long bookId, String currency, double amount) {
    }
}
```

Configuração, registrando a interface como proxy:

```java
package com.example.books.config;

import com.example.books.book.PriceClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.registry.ImportHttpServices;

@Configuration
@ImportHttpServices(types = PriceClient.class)
public class HttpServiceConfiguration {

    @Bean
    RestClientHttpServiceGroupConfigurer priceClientConfigurer() {
        return groups -> groups.forEachClient((group, builder) -> builder
                .baseUrl("http://localhost:9090")
                .build());
    }
}
```

`@ImportHttpServices` manda o Spring criar o proxy de `PriceClient` e registrá-lo como bean. O `RestClientHttpServiceGroupConfigurer` ajusta o `RestClient` que roda por trás do proxy, por grupo.

O `PriceService` fica enxuto, sem montar URL nem parsear resposta:

```java
@Service
public class PriceService {

    private final PriceClient priceClient;

    public PriceService(PriceClient priceClient) {
        this.priceClient = priceClient;
    }

    public PriceClient.PriceResponse findPrice(Long bookId) {
        return priceClient.findPrice(bookId);
    }
}
```

O proxy é injetado como qualquer outro bean.

## API versioning

O Spring 7 tem versionamento nativo. Configure a estratégia no MVC:

```java
package com.example.books.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiVersionConfiguration implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
                .useRequestHeader("API-Version")
                .addSupportedVersions("1.0", "2.0")
                .setDefaultVersion("1.0");
    }
}
```

Aqui a versão vem do header `API-Version`. Alternativas: `useQueryParam("version")`, `useMediaTypeParameter(...)`, `usePathSegment(índice)`.

No controller, o atributo `version` mapeia métodos por versão:

```java
@GetMapping(version = "1.0")
public List<Book> findAll() {
    return service.findAll();
}

@GetMapping(version = "2.0")
public BookPage findAllPaged() {
    List<Book> all = service.findAll();
    return new BookPage(all.size(), all);
}

public record BookPage(int total, List<Book> items) {
}
```

Requisição sem o header cai na versão default (`1.0`). Versão fora de `1.0`/`2.0` responde `400`. A versão pode ser resolvida por header, query param, media type ou segmento de path.

## HATEOAS (menção)

Dependência da seção:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-hateoas")
```

Hypermedia dá um passo além do JSON plano: a resposta de um recurso carrega os links das operações seguintes, e o cliente navega por eles em vez de montar a URL na mão. O starter traz o suporte, com `EntityModel` (um recurso) e `CollectionModel` (uma lista).

```java
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
```

`linkTo(methodOn(...))` exige que o método referenciado tenha retorno não-`void`, senão `linkTo` não compila (`Cannot resolve method 'linkTo(void)'`). Por isso `delete` devolve `ResponseEntity<Void>` em vez de `void`: `noContent()` mantém o `204 No Content` e dá um tipo de retorno que o link aceita.

`linkTo(methodOn(...))` gera o link a partir da anotação do método, sem hardcode de URL. O cliente recebe `_links.self` e `_links.delete` e usa o que existir. O formato é HAL (`application/hal+json`), padrão de fato de hypermedia.

HATEOAS vale quando a API precisa evoluir sem quebrar cliente, ou quando o cliente navega o fluxo dinamicamente. Pra API CRUD simples, JSON plano com versionamento (as seções acima) já resolve, e hypermedia adiciona complexidade que a maioria não usa. Trate como opcional, não como requisito de "REST de verdade".

## Estrutura

```
src/main/java/com/example/books/
├── BooksApplication.java
├── book/
│   ├── Book.java
│   ├── CreateBookRequest.java
│   ├── BookRepository.java
│   ├── BookService.java
│   ├── BookController.java
│   ├── BookHypermediaController.java
│   ├── BookNotFoundException.java
│   ├── ApiExceptionHandler.java
│   ├── PriceClient.java
│   └── PriceService.java
└── config/
    ├── HttpServiceConfiguration.java
    └── ApiVersionConfiguration.java
src/main/resources/
└── application.yaml
```
