# Curso de Spring

Atualizado para setembro de 2026. Stack fixa: Java 25 LTS, Gradle, Spring Boot 4.1 / Spring Framework 7.

Curso pra quem já programa. Sem enrolação, sem artifício de cursinho. Cada módulo entrega o que importa, com código descritivo e pontas soltas pra explorar.

## Como usar este repositório

- Existe uma pasta `labs/` onde exemplos de código podem ser gravados pra testes. Só vai pra lá quando o dono pedir.
- Código em inglês. Textos de interface e mensagens podem ser em português.
- Todo texto segue a skill stop-slop.

## Módulos

### 01. Setup moderno do Spring Boot

- Spring Boot 4.1 / Framework 7 (o que mudou vs 3.x).
- Starters modulares: `webmvc`, `restclient`, `webclient`, `data-jpa`. Autoconfigure virou 70+ módulos.
- Initializr, estrutura do projeto, Gradle, run + devtools.
- JSpecify de null-safety.
- Boot 4: Jackson 3, Jakarta EE 11, autoconfigure modularizado.

### 02. IoC e Dependency Injection

- Contexto, container, ciclo de vida, beans.
- `@Component`, `@Service`, `@Repository`, `@Configuration`, `@Bean`.
- Scopes, profiles, `@ConfigurationProperties`.
- DI por construtor, resolução de dependência, qualifiers.

### 03. Web e REST

- Controllers, routing, status, request/response handling.
- Bean Validation 3.1, tratamento de erro global, `ProblemDetail`.
- `RestClient` + HTTP interfaces `@HttpExchange` + `@ImportHttpServices` (adeus `RestTemplate`/Feign).
- API versioning nativo do Spring 7 (path, header, query, media type).
- HATEOAS (menção): `EntityModel`, links HAL.

### 04. Persistência

- Spring Data JPA + Hibernate 7, repos, derived queries, paginação.
- Specification, projections, auditing (`@EnableJpaAuditing`).
- `JdbcClient` e Spring Data JDBC.
- Cache com `@Cacheable` + Caffeine/Redis.
- NoSQL (menção): MongoDB, Redis.
- Fila com Redis (Redisson, estilo BullMQ).
- Transações, propagação, isolation.
- Flyway/Liquibase, pool HikariCP.

### 05. Segurança

- Spring Security 7 (CSRF default ligado em API, `authorizeHttpRequests`).
- Autenticação, autorização, JWT, stateless.
- Authorization Server (emissão de JWT), password encoding.
- CORS.
- MFA nativo (novo), OAuth2/OIDC, method security.
- Spring Session (JDBC/Redis), cookie flags.

### 06. Concorrência e resiliência

- Virtual threads: `spring.threads.virtual.enabled`, pinning, ajuste de pool.
- `@Async`, executors, task decoration.
- `@Scheduled` (cron, fixed delay/rate).
- Resiliência no core do Spring 7: `@Retryable`, `RetryTemplate`, `@ConcurrencyLimit` (sem lib extra).

### 07. Eventos e messaging

- `ApplicationEventPublisher`, eventos de domínio.
- Kafka, RabbitMQ (AMQP, DLQ), `JmsClient` (novo no 7).
- Spring Modulith: boundaries, `@ApplicationModuleListener`, Event Publication Registry.
- Spring Integration (menção).

### 08. Observabilidade

- Actuator, health/liveness/readiness.
- Micrometer + OpenTelemetry unificados no `spring-boot-starter-opentelemetry`.
- Logs, métricas, traces, Grafana/Prometheus/Tempo/Loki.

### 09. Testes

- JUnit 6, AssertJ, `@SpringBootTest`.
- Testcontainers, slices (`@WebMvcTest`, `@DataJpaTest`).
- Testar controller, repo, segurança, eventos.

### 10. Produção e deploy

- Profiles, config externa, secrets.
- GraalVM native image + AOT no Boot 4.
- Docker, Kubernetes, health checks.

### 11. Arquitetura

- Monólito modular vs microserviços.
- Spring Cloud (discovery, config, gateway, circuit breaker).
- MVC + virtual threads vs WebFlux (decisão de 2026).

### 12. Projeto integrador

- API completa: REST + JPA + Security + testes + observabilidade + eventos.

### 13. Spring Batch

- Job, Step e chunk processing.
- `ItemReader` / `ItemProcessor` / `ItemWriter`.
- `JobRepository`, `JobLauncher`, retomada e skip/retry.

### 14. gRPC

- Contrato em protobuf, `@GrpcService`, streaming.
- Cliente com `GrpcChannelFactory` + stub.
- gRPC vs REST.

### 15. Spring AI

- `ChatClient`, prompts, system prompt.
- Tool calling com `@Tool`.
- RAG com embeddings + vector store.
- Advisors (memória, `QuestionAnswerAdvisor`), MCP.