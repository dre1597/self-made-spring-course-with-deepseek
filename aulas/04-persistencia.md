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

No repository, o tipo de retorno vira a projection. O repositório completo, com o método novo junto dos derivados:

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

    List<BookSummary> findSummariesByAuthor(String author);
}
```

O `findSummariesByAuthor` é método separado, pra não sobrescrever o `findByAuthor` que devolve a entity completa. O Spring gera o `SELECT` só de `title` e `author`. Bom pra listagem e relatório onde a entity carrega mais do que precisa. Pra combinar com `@Query`, o alias do JPQL tem que bater com o nome do getter.

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

O starter-security também protege os endpoints: toda requisição precisa autenticar e mutações exigem token CSRF. Produção quer isso; uma API consumida por um cliente sem sessão, não. Então o `application.yaml` fixa a senha e o código desliga o CSRF:

```yaml
spring:
  security:
    user:
      name: user
      password: user123
```

A senha fixa evita a senha aleatória que o Boot gera a cada restart — a que muda sozinha e derruba a autenticação no meio do caminho.

```java
package com.example.books.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> {
                })
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .build();
    }
}
```

O bean `SecurityFilterChain` substitui a chain default do Boot. Sem ele, a default tem CSRF ligado e basic auth embutido; com ele, você declara o que quer. O `csrf` desligado e o `httpBasic` explícito é a config de API consumida por cliente sem sessão, como o cliente HTTP da IDE. CSRF só protege sessão navegada por formulário; quem manda `Authorization` por request não usa. A aula de segurança aprofunda isso.

Detalhe pra auditoria: os campos `@CreatedBy`/`@LastModifiedBy` dependem de `Authentication`. Um `POST` autenticado grava `createdBy = user`; um insert feito em contexto sem login (seed, job) grava `null`. Os campos de data (`@CreatedDate`/`@LastModifiedDate`) o Spring preenche nos dois casos.

## Cache

O serviço que cacheia lança uma exceção de domínio quando o livro não existe:

```java
package com.example.books.book;

public class BookNotFoundException extends RuntimeException {

    public BookNotFoundException() {
        super("Livro não encontrado");
    }

    public BookNotFoundException(Long id) {
        super("Livro " + id + " não encontrado");
    }
}
```

`@Cacheable` guarda o retorno de um método e devolve o cacheado sem reexecutar. Leitura quente e custosa entra aqui:

```java
package com.example.books.book;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookCatalogService {

    private static final Logger logger = LoggerFactory.getLogger(BookCatalogService.class);

    private final BookRepository repository;

    public BookCatalogService(BookRepository repository) {
        this.repository = repository;
    }

    @Cacheable("books")
    @Transactional(readOnly = true)
    public Book findById(Long id) {
        logger.info("Cache miss: buscando livro {} no banco", id);
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

O `logger` do `BookCatalogService` é o ponto de observação: o método inteiro é pulado quando o cache acerta, então o log `Cache miss` só aparece na primeira execução. Duas chamadas seguidas a `GET /books/{id}` (seção ponta a ponta no fim da aula) mostram o log uma vez só. Como o cache não é invalidado em lugar nenhum desta aula, um livro criado depois de ser cacheado continua servindo a versão velha — é o trade-off que o `@CacheEvict` resolve.

## NoSQL (menção)

O Spring Data cobre NoSQL com o mesmo desenho de interface + convenção. Os dois que mais aparecem:

- **MongoDB** (`spring-boot-starter-data-mongodb`): `@Document` no lugar de `@Entity`, `MongoRepository` no lugar de `JpaRepository`. Documento de shape flexível, sem schema rígido.
- **Redis** (`spring-boot-starter-data-redis`): `RedisTemplate` pra estrutura em memória (cache, fila, contador, lock) e `@RedisHash` pra repositório de objeto com TTL.

A escolha segue a mesma régua de JPA vs JDBC. Mongo quando o dado é um documento autônomo que muda de forma. Redis quando precisa de acesso rápido a estrutura volátil. Nenhum substitui o relacional quando o dado tem relações e integridade forte; eles entram como segunda fonte, convivendo com o Postgres.

## Fila com Redis (estilo BullMQ)

Redis vira broker de fila quando o volume é leve e você já tem Redis na stack, então não sobe um broker só pra isso. O BullMQ do Node resolve delayed jobs com uma agenda: cada job entra num sorted set com o timestamp de vencimento como score, e um worker move pra fila de processamento os jobs cujo vencimento chegou. O mesmo desenho no Java com Redisson: um `RScoredSortedSet` pra agenda e uma `RBlockingQueue` pro processamento.

A dependência da seção já vai com a versão fixada:

```kotlin
implementation("org.redisson:redisson-spring-boot-starter:4.7.0")
```

Os starters `org.springframework.boot:*` não levam versão porque o BOM do Boot gerencia. O Redisson fica fora desse BOM, então a versão é explícita. Ela precisa ser da linha 4.x, a que conversa com o Boot 4; a 3.x foi feita pro Boot 3 e quebra na modularização do autoconfigure. O starter auto-configura o `RedissonClient` apontando pro Redis em `localhost:6379`. Sem ele, não existe fila nem client Redis na aplicação.

Agendador: grava um lembrete de devolução com vencimento daqui a um tempo.

```java
package com.example.books.notification;

import java.time.Duration;

import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
public class ReturnReminderScheduler {

    private final RScoredSortedSet<Long> scheduledReminders;

    public ReturnReminderScheduler(RedissonClient redisson) {
        this.scheduledReminders = redisson.getScoredSortedSet("return-reminder-agenda");
    }

    public void schedule(Long loanId, Duration delay) {
        double dueAt = System.currentTimeMillis() + delay.toMillis();
        scheduledReminders.add(dueAt, loanId);
    }
}
```

O score é o instante de vencimento em epoch millis. O sorted set mantém os itens ordenados pelo vencimento, então o primeiro da agenda é sempre o lembrete mais próximo. Nada sai dali até o tempo passar.

Dispatcher: move os lembretes vencidos da agenda pra fila de processamento.

```java
package com.example.books.notification;

import org.redisson.api.RBlockingQueue;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ReturnReminderDispatcher {

    private static final long DISPATCH_INTERVAL_MILLIS = 1_000;

    private final RScoredSortedSet<Long> scheduledReminders;
    private final RBlockingQueue<Long> dueReminders;

    public ReturnReminderDispatcher(RedissonClient redisson) {
        this.scheduledReminders = redisson.getScoredSortedSet("return-reminder-agenda");
        this.dueReminders = redisson.getBlockingQueue("return-reminders");
    }

    @PostConstruct
    void start() {
        Thread.ofVirtual().start(this::dispatch);
    }

    private void dispatch() {
        while (true) {
            try {
                Double dueAt = scheduledReminders.firstScore();
                if (dueAt == null || dueAt > System.currentTimeMillis()) {
                    Thread.sleep(DISPATCH_INTERVAL_MILLIS);
                    continue;
                }
                Long loanId = scheduledReminders.pollFirst();
                if (loanId != null) {
                    dueReminders.offer(loanId);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
```

Um sorted set não bloqueia ninguém esperando item novo, então o dispatcher faz polling: olha o primeiro score da agenda; se ainda não venceu (ou a agenda está vazia), dorme um segundo e volta; se venceu, `pollFirst()` remove e devolve o item e o `offer` coloca na fila de processamento. O intervalo decide o atraso máximo que um lembrete já vencido espera pra ser movido. Curto demais acorda à toa; longo demais atrasa a entrega. Uma alternativa é dormir até o vencimento do próximo item, com um teto pra não ignorar um job novo com delay curto.

Consumidor: `take()` bloqueia até vir job, e roda numa virtual thread.

```java
package com.example.books.notification;

import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ReturnReminderConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ReturnReminderConsumer.class);

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
        logger.info("Lembrete de devolução enviado pro empréstimo {}", loanId);
    }
}
```

Agenda (`return-reminder-agenda`) e fila (`return-reminders`) convivem: o agendador só escreve na agenda, o dispatcher move da agenda pra fila, o consumidor tira da fila. O Redisson resolve a serialização do `Long` sozinho. Como a agenda fica no Redis, um restart do consumidor ou do dispatcher não perde lembrete pendente: ele ainda está no sorted set e é movido quando a aplicação volta.

O Redisson tinha uma API pronta pra isso, a `RDelayedQueue`, deprecada na linha 4.x: perdia mensagem e agendava de forma não confiável, e a reescrita (a `RReliableQueue`) só existe na edição PRO. Na Community Edition o desenho de agenda acima é o caminho sem API deprecada, e é o mesmo mecanismo que o BullMQ usa por baixo.

A agenda + fila cobre o caso leve: lembrete que tolera alguns segundos de atraso e não se perde num restart. Pra volume alto ou entrega com garantia de replay, não segura: vai de Kafka ou Artemis. O mesmo `RedissonClient` ainda entrega `RLock` (lock) e `RAtomicLong` (contador) pro resto do domínio.

Esta seção é a única da aula que precisa de infraestrutura externa; o resto roda no H2 em memória. O starter aponta pro Redis em `localhost:6379`. Um `compose.yaml` na raiz do projeto sobe o Redis:

```yaml
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

`docker compose up -d` sobe, `docker compose down` derruba. Com o Redis de pé o `RedissonClient` conecta sozinho; sem ele, as operações de fila falham ao tentar alcançar o broker.

Pra ver a fila andar falta alguém chamar o `schedule`. No domínio real isso acontece quando o empréstimo é criado; aqui um controller de demonstração expõe o agendamento por HTTP, sem criar o domínio de empréstimo:

```java
package com.example.books.notification;

import java.time.Duration;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/return-reminders")
public class ReturnReminderController {

    private final ReturnReminderScheduler scheduler;

    public ReturnReminderController(ReturnReminderScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @PostMapping("/{loanId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void schedule(@PathVariable Long loanId,
                         @RequestParam(defaultValue = "5") long delaySeconds) {
        scheduler.schedule(loanId, Duration.ofSeconds(delaySeconds));
    }
}
```

`202 Accepted` responde que o trabalho entrou e roda depois; não há o que retornar no corpo. O request de teste vive num arquivo `.http` da IDE, que a seção ponta a ponta junta num arquivo só. Solto, ele fica assim:

```http
POST http://localhost:8080/return-reminders/42?delaySeconds=5
Authorization: Basic user user123
```

A resposta vem `202` na hora e o lembrete fica na agenda do Redis até vencer. A senha fixa vem da seção de auditing, onde o starter-security entrou e a `SecurityConfiguration` liberou o basic auth sem CSRF. Uns 6 segundos depois (5 do delay mais até 1 do polling do dispatcher), o log do consumidor confirma a entrega:

```text
Lembrete de devolução enviado pro empréstimo 42
```

O `notifyMember` loga porque não há serviço de notificação de verdade nesta aula; no sistema real ele chamaria o e-mail ou o push. Dá pra espiar o Redis por fora: `docker compose exec redis redis-cli zcard return-reminder-agenda` mostra a agenda, e `llen return-reminders` a fila; a agenda esvazia e a fila ganha o item assim que o lembrete vence.

## Transações

`@Transactional` delimita a unidade de trabalho. Se algo lança exceção, o que foi feito desfaz.

```java
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
```

`readOnly = true` evita dirty checking e otimiza a conexão em consulta pura. O `search` monta a `Specification` com as condições não-nulas: nenhum filtro vira um `findAll` sem predicado, autor vira a condição de igualdade, fragmento vira o `LIKE`. É o padrão da seção de Specification aplicado num endpoint.

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
```

O `BookService.delete` chama `recordDeletion` depois do `deleteById`. A auditoria roda em transação própria: se o delete falhar e rolar back, o log de auditoria já gravado não some junto. A tabela `audit_log` vem de migração (seção Flyway).

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
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);
```

A `V1` já nasce com as colunas de auditoria: ela precisa bater com a `Book` final da seção de Auditing, porque com Flyway o schema é dele e o Hibernate só valida. As outras duas tabelas que a aula usa também têm migração própria:

```sql
-- V2__create_review_table.sql
CREATE TABLE reviews (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    book_id BIGINT NOT NULL,
    comment VARCHAR(1000) NOT NULL
);
```

```sql
-- V3__create_audit_log_table.sql
CREATE TABLE audit_log (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    book_id BIGINT NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

Cada tabela não-JPA tem migração: `reviews` (Spring Data JDBC, seção da `Review`) e `audit_log` (o `BookAuditService`). Sem elas, essas seções quebram com "tabela não existe", porque o Hibernate só cria schema de entidade `@Entity` e o Spring Data JDBC nunca cria schema. Com H2 em memória, o schema nasce vazio a cada boot e o Flyway o monta inteiro do zero.

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

## Aula inteira testável (ponta a ponta)

Cada conceito desta aula precisa de gatilho e de um jeito de ver o resultado. Os repositórios e serviços acima ficam órfãos sem um endpoint que os chame, e o H2 nasce vazio a cada boot. Esta seção fecha os dois: uma API REST exercita cada seção e um seed garante dado pra consulta. Os testes manuais rodam da própria IDE, num arquivo `.http`.

O seed dá matéria-prima pras derived queries, Specification, projection e relatório:

```java
package com.example.books.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.books.book.Book;
import com.example.books.book.BookRepository;

@Configuration
public class SampleDataConfiguration {

    @Bean
    CommandLineRunner sampleBooks(BookRepository repository) {
        return args -> {
            repository.save(new Book("A Hora da Estrela", "Clarice Lispector"));
            repository.save(new Book("Água Viva", "Clarice Lispector"));
            repository.save(new Book("Dom Casmurro", "Machado de Assis"));
        };
    }
}
```

O `CommandLineRunner` roda depois que o contexto sobe. O H2 é em memória e nasce vazio a cada boot, então o seed re-insere sem duplicar. `createdBy` fica `null` (não há `Authentication` no seed); `createdAt` e `updatedAt` o auditing preenche.

A API começa pelo DTO de criação e pelo controller que expõe cada seção:

```java
package com.example.books.book;

import jakarta.validation.constraints.NotBlank;

public record CreateBookRequest(@NotBlank String title, @NotBlank String author) {
}
```

```java
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
```

O mapeamento endpoint → seção:

- `GET /books` → paginação e derived query (`Pageable`)
- `GET /books/latest` → `findFirstByOrderByCreatedAtDesc`
- `GET /books/by-author/{author}` e `GET /books/by-title/{fragment}` → derived queries `findByAuthor` / `findByTitleContainingIgnoreCase`
- `GET /books/search` → Specification composta
- `GET /books/summary` → projection
- `GET /books/report/per-author` → JdbcClient
- `GET /books/{id}` → cache (`BookCatalogService`)
- `POST /books` → criação com auditoria
- `DELETE /books/{id}` → exclusão com auditoria `REQUIRES_NEW`

A `BookNotFoundException` vira `404` com um handler:

```java
package com.example.books.book;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class BookExceptionHandler {

    @ExceptionHandler(BookNotFoundException.class)
    public ProblemDetail handleNotFound(BookNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }
}
```

A `Review` (Spring Data JDBC) ganha controller próprio:

```java
package com.example.books.review;

import jakarta.validation.constraints.NotBlank;

public record CreateReviewRequest(@NotBlank String comment) {
}
```

```java
package com.example.books.review;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/books/{bookId}/reviews")
public class ReviewController {

    private final ReviewRepository repository;

    public ReviewController(ReviewRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Review> findByBook(@PathVariable Long bookId) {
        return repository.findByBookId(bookId);
    }

    @PostMapping
    public Review create(@PathVariable Long bookId, @RequestBody CreateReviewRequest request) {
        return repository.save(new Review(bookId, request.comment()));
    }
}
```

Os testes manuais ficam em `src/main/resources/requests.http`. O IntelliJ mostra cada request com um ícone de play na lateral; um clique executa e abre o painel de resposta, sem sair da IDE. As variáveis `@host`, `@user` e `@password` do topo valem pro arquivo inteiro — a senha é a fixa da seção de auditing, e o `Basic` com user e password separados o IntelliJ codifica sozinho.

```http
@host = http://localhost:8080
@user = user
@password = user123

### Derived query: livros por autor
GET {{host}}/books/by-author/Clarice%20Lispector
Authorization: Basic {{user}} {{password}}

### Derived query: livros por fragmento no título
GET {{host}}/books/by-title/hora
Authorization: Basic {{user}} {{password}}

### Derived query: último livro criado
GET {{host}}/books/latest
Authorization: Basic {{user}} {{password}}

### Paginação
GET {{host}}/books?page=0&size=2
Authorization: Basic {{user}} {{password}}

### Specification: autor e fragmento combinados
GET {{host}}/books/search?author=Clarice%20Lispector&fragment=hora
Authorization: Basic {{user}} {{password}}

### Specification: só fragmento
GET {{host}}/books/search?fragment=viva
Authorization: Basic {{user}} {{password}}

### Projection: só title e author
GET {{host}}/books/summary?author=Clarice%20Lispector
Authorization: Basic {{user}} {{password}}

### JdbcClient: relatório de livros por autor
GET {{host}}/books/report/per-author
Authorization: Basic {{user}} {{password}}

### Cache: executa duas vezes; a segunda vem do cache
GET {{host}}/books/1
Authorization: Basic {{user}} {{password}}

### Auditing: criação autenticada preenche createdBy e createdAt
POST {{host}}/books
Authorization: Basic {{user}} {{password}}
Content-Type: application/json

{
  "title": "Memórias Póstumas de Brás Cubas",
  "author": "Machado de Assis"
}

### Spring Data JDBC: cria review
POST {{host}}/books/1/reviews
Authorization: Basic {{user}} {{password}}
Content-Type: application/json

{
  "comment": "Ótimo"
}

### Spring Data JDBC: lista reviews
GET {{host}}/books/1/reviews
Authorization: Basic {{user}} {{password}}

### Transações: exclusão grava auditoria em transação própria
DELETE {{host}}/books/3
Authorization: Basic {{user}} {{password}}

### Fila com Redis: agenda lembrete de devolução
POST {{host}}/return-reminders/42?delaySeconds=5
Authorization: Basic {{user}} {{password}}
```

O que cada grupo prova:

- Derived queries, paginação e Specification respondem no corpo JSON; `/books/search?fragment=viva` mostra a condição opcional sozinha, sem autor.
- Projection devolve só `title` e `author`; relatório devolve `[{author, total}, ...]` ordenado por total.
- Auditing: o POST devolve o livro com `createdBy = "user"` e `createdAt` preenchido.
- Cache: rode o GET de `/books/1` duas vezes em sequência; o console imprime `Cache miss` só na primeira.
- Transações: o DELETE devolve `204` e o console loga `Auditoria de exclusão gravada pro livro 3`; o GET de `/books/3` depois devolve `404` com `ProblemDetail`.

O Redis do compose só é necessário pro request da fila. `reviews` e `audit_log` dependem das migrations `V2` e `V3` da seção Flyway — até o Flyway assumir o schema, os requests de review e de exclusão quebram com "tabela não existe".

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
│   ├── BookService.java
│   ├── BookCatalogService.java
│   ├── BookController.java
│   ├── BookExceptionHandler.java
│   ├── BookNotFoundException.java
│   ├── BookReportRepository.java
│   └── BookAuditService.java
├── review/
│   ├── Review.java
│   ├── CreateReviewRequest.java
│   ├── ReviewRepository.java
│   └── ReviewController.java
├── notification/
│   ├── ReturnReminderScheduler.java
│   ├── ReturnReminderDispatcher.java
│   ├── ReturnReminderConsumer.java
│   └── ReturnReminderController.java
└── config/
    ├── JpaAuditingConfiguration.java
    ├── CacheConfiguration.java
    ├── SecurityConfiguration.java
    └── SampleDataConfiguration.java
src/main/resources/
├── application.yaml
├── requests.http
└── db/migration/
    ├── V1__create_book_table.sql
    ├── V2__create_review_table.sql
    └── V3__create_audit_log_table.sql
```
