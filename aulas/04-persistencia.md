# Aula 04 — Persistência

Objetivo: persistir com Spring Data JPA, cair pro JDBC quando JPA não compensa, e governar schema com Flyway.

As dependências entram nas seções onde são usadas, cada uma na sua hora. A API e a validação dos requests sustentam a aula inteira, então entram logo aqui, na primeira parada:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-validation")
```

## Entity e Repository

Dependências desta seção:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
runtimeOnly("com.h2database:h2")
```

`data-jpa` traz Hibernate 7 e o HikariCP (via JDBC). O `h2` é o banco em memória, `runtimeOnly` porque só o driver precisa existir na hora de rodar, não em tempo de compilação.

Entity mapeada:

```java
package com.example.books.book;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Book() {
    }

    public Book(String title, String author) {
        this.title = title;
        this.author = author;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

O construtor `protected` vazio é exigência do Hibernate, que instancia a entity por reflexão. `GenerationType.IDENTITY` delega o id pro banco.

Repository:

```java
package com.example.books.book;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    List<Book> findByAuthor(String author);

    List<Book> findByTitleContainingIgnoreCase(String fragment);

    Optional<Book> findFirstByOrderByCreatedAtDesc();

    Page<Book> findByAuthor(String author, Pageable pageable);
}
```

Só a interface; o Spring Data gera a implementação. `findByAuthor` e `findByTitleContainingIgnoreCase` são derived queries: o nome do método vira o predicado.

## Derived queries e paginação

A convenção de nome compõe a consulta a partir do nome do método:

| Prefixo | Significado |
|---|---|
| `findByAuthor` | `WHERE author = ?` |
| `findByTitleContainingIgnoreCase` | `WHERE title ILIKE %?%` |
| `findFirstBy...OrderByCreatedAtDesc` | limita a 1 e ordena desc |
| `findByAuthor(..., Pageable)` | mesma consulta, paginada |

Paginação:

```java
Pageable page = PageRequest.of(0, 20, Sort.by("title"));
Page<Book> firstPage = repository.findByAuthor("Clarice", page);
```

`Page` devolve os itens da página, o total geral e a posição. Quando o predicado fica grande demais pra caber no nome do método, use `@Query` com JPQL. Quando nem JPQL resolve, `JdbcClient`.

## Specification

Derived query vira explosão quando a consulta tem muitos filtros opcionais. `Specification` monta o predicado em código, compondo condições em runtime. Cada condição vira um método estático:

```java
package com.example.books.book;

import org.springframework.data.jpa.domain.Specification;

public final class BookSpecifications {

    private BookSpecifications() {
    }

    public static Specification<Book> byAuthor(String author) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("author"), author);
    }

    public static Specification<Book> titleContains(String fragment) {
        return (root, query, criteriaBuilder) -> criteriaBuilder
                .like(criteriaBuilder.lower(root.get("title")), "%" + fragment.toLowerCase() + "%");
    }
}
```

O `JpaSpecificationExecutor` (na interface do repository, acima) expõe `findAll(Specification)`. As condições se combinam com `and`, `or` e `not`:

```java
Specification<Book> busca = BookSpecifications.byAuthor("Clarice")
        .and(BookSpecifications.titleContains("hora"));
List<Book> resultados = repository.findAll(busca);
```

Specification é o caminho pra tela de busca com filtros que o usuário pode ou não preencher: o predicado é o conjunto das condições não-nulas, sem gerar método derivado pra cada combinação.

## Projections

Projection devolve só as colunas que interessam, em vez da entity inteira. Declare uma interface com os getters dos campos que quer:

```java
package com.example.books.book;

public interface BookSummary {

    String getTitle();

    String getAuthor();
}
```

No repository, o tipo de retorno vira a projection:

```java
List<BookSummary> findByAuthor(String author);
```

O Spring gera o `SELECT` só de `title` e `author`. Bom pra listagem e relatório onde a entity carrega mais do que precisa. Pra combinar com `@Query`, o alias do JPQL tem que bater com o nome do getter.

## JdbcClient

Pra agregações e relatórios que o JPA não modela bem, o `JdbcClient` dá SQL cru com mapeamento direto:

```java
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
```

O Boot expõe o `JdbcClient` pronto, em cima do `DataSource` e do `JdbcTemplate`. O `GROUP BY` acima não tem equivalente ergonômico em derived query, então SQL cru é o caminho.

## Spring Data JDBC

Esta aula mostra dois mundos de persistência no mesmo projeto: JPA (o `Book`, com Hibernate) e Spring Data JDBC (o `Review`, sem Hibernate). Os dois convivem porque o Boot autoconfigura cada starter separado, e cada um escaneia seus próprios repositórios. Por isso a seção tem dependência própria:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
```

É ela que coloca o pacote `org.springframework.data.relational` no classpath, onde mora o `@Table` do `Review`. Sem ela, o import não compila, mesmo com o `data-jpa` presente.

JDBC é a alternativa sem Hibernate: mapeia direto pra tabela, sem contexto de persistência, sem lazy loading. Bom pra domínios simples e agregados.

```java
package com.example.books.review;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("reviews")
public class Review {

    @Id
    private Long id;

    private Long bookId;

    private String comment;

    public Review(Long bookId, String comment) {
        this.bookId = bookId;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getComment() {
        return comment;
    }
}
```

```java
package com.example.books.review;

import java.util.List;

import org.springframework.data.repository.CrudRepository;

public interface ReviewRepository extends CrudRepository<Review, Long> {

    List<Review> findByBookId(Long bookId);
}
```

JPA usa `JpaRepository`; JDBC usa `CrudRepository`. A diferença prática: JPA pra gráficos ricos de entidades com cache e lazy; JDBC pra leitura/escrita direta, sem surpresas de consultas geradas.

## Auditing

Dependência da seção:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-security")
```

Ela fornece o `SecurityContextHolder` e o `Authentication`, de onde o `AuditorAware` tira o usuário corrente. Auditoria grava quem criou/alterou e quando. Os campos entram direto na entidade, cada uma declarando o que tem, com o `AuditingEntityListener` na classe. A `Book` com os quatro campos de auditoria:

```java
package com.example.books.book;

import java.time.Instant;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "books")
@EntityListeners(AuditingEntityListener.class)
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;

    protected Book() {
    }

    public Book(String title, String author) {
        this.title = title;
        this.author = author;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }
}
```

`@EntityListeners(AuditingEntityListener.class)` liga o mecanismo pra essa classe. Cada anotação marca o papel do campo: `@CreatedDate` e `@LastModifiedDate` o Spring preenche sozinho; `@CreatedBy` e `@LastModifiedBy` saem do `AuditorAware`, logo abaixo.

Declarar na própria entidade tem uma vantagem sobre superclasse: cada entidade carrega só o que faz sentido. Entidade criada por job (sem usuário logado) não tem `createdBy`/`updatedBy`; entidade imutável não tem `updatedAt`; a que usa soft delete declara o `deletedAt` onde ele existe. Nada chega por herança escondida — o trade-off é repetir as anotações, que é o custo aceito pelo critério de explícito valer mais que curto.

`@CreatedDate` dispensa o `createdAt` manual no construtor. O usuário vem de um `AuditorAware`:

```java
package com.example.books.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfiguration {

    @Bean
    AuditorAware<String> auditorProvider() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(Authentication::getName);
    }
}
```

`@EnableJpaAuditing` liga o mecanismo; o `AuditorAware` diz de onde sai o usuário corrente.

## Cache

`@Cacheable` guarda o retorno de um método e devolve o cacheado sem reexecutar. Leitura quente e custosa entra aqui:

```java
package com.example.books.book;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookCatalogService {

    private final BookRepository repository;

    public BookCatalogService(BookRepository repository) {
        this.repository = repository;
    }

    @Cacheable("books")
    @Transactional(readOnly = true)
    public Book findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }
}
```

Ligue o cache e escolha o provider:

```java
package com.example.books.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfiguration {
}
```

```kotlin
implementation("org.springframework.boot:spring-boot-starter-cache")
implementation("com.github.ben-manes.caffeine:caffeine")
```

As duas andam juntas, mas fazem papéis diferentes. `starter-cache` é a abstração: as anotações `@Cacheable`/`@CacheEvict`/`@CachePut`, o `CacheManager` e a config `spring.cache.*`. Caffeine é a implementação de verdade, a máquina que guarda e expira os valores em memória. Programando contra o `starter-cache`, troca-se o provider depois (Redis, por exemplo) sem mexer no código.

```yaml
spring:
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=10m
```

O `type: caffeine` diz ao Boot qual implementação usar; a `spec` configura o Caffeine, `maximumSize` de 1000 entradas e `expireAfterWrite` de 10 minutos.

A key default são os argumentos do método. Quando o dado muda, `@CacheEvict` limpa a entrada; quando a operação atualiza o valor, `@CachePut` grava sem pular o método. Caffeine é cache local (por nó); Redis (`spring-boot-starter-data-redis`) vira o mesmo cache compartilhado entre instâncias. O problema do cache nunca é guardar, é invalidar no momento certo.

## NoSQL (menção)

O Spring Data cobre NoSQL com o mesmo desenho de interface + convenção. Os dois que mais aparecem:

- **MongoDB** (`spring-boot-starter-data-mongodb`): `@Document` no lugar de `@Entity`, `MongoRepository` no lugar de `JpaRepository`. Documento de shape flexível, sem schema rígido.
- **Redis** (`spring-boot-starter-data-redis`): `RedisTemplate` pra estrutura em memória (cache, fila, contador, lock) e `@RedisHash` pra repositório de objeto com TTL.

A escolha segue a mesma régua de JPA vs JDBC. Mongo quando o dado é um documento autônomo que muda de forma. Redis quando precisa de acesso rápido a estrutura volátil. Nenhum substitui o relacional quando o dado tem relações e integridade forte; eles entram como segunda fonte, convivendo com o Postgres.

## Fila com Redis (estilo BullMQ)

Redis vira broker de fila quando o volume é leve e você já tem Redis na stack, então não sobe um broker só pra isso. No Node o BullMQ faz esse papel com delayed jobs e retry. No Java o equivalente é o Redisson: `RBlockingQueue` no consumidor e `RDelayedQueue` no agendamento. A dependência da seção:

```kotlin
implementation("org.redisson:redisson-spring-boot-starter")
```

Ela auto-configura o `RedissonClient` apontando pro Redis em `localhost:6379`. Sem ela, não existe fila nem client Redis na aplicação.

Produtor: enfileira um lembrete de devolução pra daqui um tempo.

```java
package com.example.books.notification;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RDelayedQueue;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
public class ReturnReminderQueue {

    private final RDelayedQueue<Long> delayedQueue;

    public ReturnReminderQueue(RedissonClient redisson) {
        RQueue<Long> queue = redisson.getQueue("return-reminders");
        this.delayedQueue = redisson.getDelayedQueue(queue);
    }

    public void schedule(Long loanId, Duration delay) {
        delayedQueue.offer(loanId, delay.toMillis(), TimeUnit.MILLISECONDS);
    }
}
```

`getDelayedQueue(queue)` cria o delayed queue por cima da fila real. O `offer` com delay só joga o job na fila quando o tempo passa, igual ao delayed jobs do BullMQ.

Consumidor: `take()` bloqueia até vir job, e roda numa virtual thread.

```java
package com.example.books.notification;

import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ReturnReminderConsumer {

    private final RBlockingQueue<Long> queue;

    public ReturnReminderConsumer(RedissonClient redisson) {
        this.queue = redisson.getBlockingQueue("return-reminders");
    }

    @PostConstruct
    void start() {
        Thread.ofVirtual().start(this::consume);
    }

    private void consume() {
        while (true) {
            try {
                Long loanId = queue.take();
                notifyMember(loanId);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void notifyMember(Long loanId) {
        // envia o lembrete de devolução
    }
}
```

O produtor e o consumidor falam com a mesma chave (`return-reminders`); o Redisson resolve a serialização do `Long` sozinho. Pra volume alto ou entrega com garantia de replay, isso aqui não segura: vai de Kafka ou Artemis. Redis como fila é o caso leve, e o Redisson ainda entrega `RLock` (lock) e `RAtomicLong` (contador) com o mesmo `RedissonClient`.

## Transações

`@Transactional` delimita a unidade de trabalho. Se algo lança exceção, o que foi feito desfaz.

```java
package com.example.books.book;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Book> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public Book findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new BookNotFoundException(id));
    }

    @Transactional
    public Book create(CreateBookRequest request) {
        return repository.save(new Book(request.title(), request.author()));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
```

`readOnly = true` evita dirty checking e otimiza a conexão em consulta pura.

Propagação define como o método se junta a uma transação já aberta:

| Propagação | Comportamento |
|---|---|
| `REQUIRED` (default) | usa a transação corrente, ou abre uma nova |
| `REQUIRES_NEW` | sempre abre uma nova, suspendendo a corrente |
| `MANDATORY` | exige transação existente, senão falha |
| `SUPPORTS` | usa se existir, senão roda sem |

Isolation define o nível de isolamento:

| Isolation | Comportamento |
|---|---|
| `DEFAULT` (default) | segue o banco |
| `READ_COMMITTED` | lê só dados commitados |
| `REPEATABLE_READ` | leituras repetidas veem o mesmo dado |
| `SERIALIZABLE` | transações serializadas |

Um exemplo de `REQUIRES_NEW`: gravar uma auditoria em transação separada, pra ela commit mesmo se a operação principal falhar.

```java
@Service
public class BookAuditService {

    private final JdbcClient jdbcClient;

    public BookAuditService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDeletion(Long bookId) {
        jdbcClient.sql("INSERT INTO audit_log (book_id) VALUES (:bookId)")
                .param("bookId", bookId)
                .update();
    }
}
```

## Flyway e HikariCP

A dependência da seção:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-flyway")
```

Flyway aplica migrações versionadas na subida, antes do JPA usar o schema. No Boot 4 só funciona via starter; `flyway-core` solto no classpath não é mais auto-configurado. Migração em `src/main/resources/db/migration/V1__create_book_table.sql`:

```sql
CREATE TABLE books (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

Com Flyway no classpath, o schema é dele. O Hibernate entra em modo `validate`, checando que as entities batem com as tabelas em vez de recriar:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Sem Flyway, o Boot usaria `create-drop` no H2 embedded, recriando o schema a cada subida. Com Flyway, o `ddl-auto` vira `none` por padrão; `validate` é o recomendado pra pegar divergência cedo.

HikariCP é o pool default: vem com o `data-jpa` e gerencia as conexões. Ajuste fino via `spring.datasource.hikari.*`:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
```

## Estrutura

```
src/main/java/com/example/books/
├── BooksApplication.java
├── book/
│   ├── Book.java
│   ├── CreateBookRequest.java
│   ├── BookRepository.java
│   ├── BookSpecifications.java
│   ├── BookSummary.java
│   ├── AuditableEntity.java
│   ├── BookService.java
│   ├── BookCatalogService.java
│   ├── BookController.java
│   ├── BookNotFoundException.java
│   ├── BookReportRepository.java
│   └── BookAuditService.java
├── review/
│   ├── Review.java
│   └── ReviewRepository.java
├── notification/
│   ├── ReturnReminderQueue.java
│   └── ReturnReminderConsumer.java
└── config/
    ├── JpaAuditingConfiguration.java
    └── CacheConfiguration.java
src/main/resources/
├── application.yaml
└── db/migration/
    └── V1__create_book_table.sql
```
