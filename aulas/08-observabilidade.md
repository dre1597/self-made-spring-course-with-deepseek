# Aula 08 — Observabilidade

Objetivo: expor health checks, exportar métricas e traces via OTLP e correlacionar logs.

## Dependências

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0")
}
```

O `actuator` traz health checks, métricas e o `ObservationRegistry`. O `spring-boot-starter-opentelemetry` é o starter novo do Boot 4: unifica Micrometer e OpenTelemetry num pacote só, exportando tudo via OTLP. O logback appender joga os logs também via OTLP.

## Actuator e health checks

O Actuator expõe endpoints de gestão:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      probes:
        enabled: true
```

Com isso:

- `/actuator/health` — saúde geral da aplicação.
- `/actuator/health/liveness` — a aplicação tá viva (pra Kubernetes reiniciar se morrer).
- `/actuator/health/readiness` — a aplicação tá pronta (pra Kubernetes parar de mandar tráfego quando não está).
- `/actuator/metrics` — lista as métricas; `/actuator/metrics/jvm.memory.used` mostra uma específica.

Liveness e readiness só existem com `probes.enabled: true`. A diferença importa no Kubernetes: liveness falhou mata o pod; readiness falhou só tira do load balancer.

## Micrometer + OpenTelemetry

No Boot 3 você escolhia um registry por backend (Prometheus, Datadog...). No Boot 4 o `spring-boot-starter-opentelemetry` troca isso por um formato único: OTLP. A API do Micrometer continua a mesma; o que muda é o caminho de exportação.

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    metrics:
      export:
        url: http://localhost:4318/v1/metrics
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/traces
    logging:
      export:
        otlp:
          endpoint: http://localhost:4318/v1/logs
```

Métricas e traces saem em OTLP pra qualquer backend compatível. `sampling.probability: 1.0` amostra 100% dos traces em dev; em produção baixe pra evitar volume desnecessário.

## Métricas de negócio

O Boot já instrumenta JVM, HTTP e banco. Métrica de negócio você cria com `MeterRegistry`:

```java
package com.example.books.book;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Service;

@Service
public class BookService {

    private final Counter createdBooks;

    public BookService(MeterRegistry meterRegistry) {
        this.createdBooks = Counter.builder("books.created")
                .description("Livros criados")
                .register(meterRegistry);
    }

    public Book create(CreateBookRequest request) {
        Book book = new Book(request.title(), request.author());
        createdBooks.increment();
        return book;
    }
}
```

`Counter` pra eventos cumulativos. Pra duração, `Timer`; pra valor corrente, `Gauge`.

## Observações com @Observed

Um `@Observed` gera métrica e trace ao mesmo tempo:

```java
@Observed(name = "books.create")
public Book create(CreateBookRequest request) {
    return repository.save(new Book(request.title(), request.author()));
}
```

A chamada vira um timer `books.create` e um span de trace com o mesmo nome. O `@Observed` precisa do `ObservationRegistry` (que o actuator já provê) e de AOP habilitado, que o Boot liga automaticamente com Micrometer Tracing no classpath.

## Logs correlacionados

O logback appender manda os logs via OTLP e injeta `trace_id` e `span_id` em cada linha:

```
2026-09-07T15:34:56Z INFO [books,12f8ab3d9c0a4b7e,24d1e7c0b5a9f3e2] Livro criado
```

O segundo campo é o `trace_id`, o terceiro o `span_id`. Com eles, você pula do log pro trace no backend.

## Grafana, Prometheus, Tempo, Loki

O stack LGTM cobre os três sinais:

- **Prometheus** — métricas (ou o Grafana recebe OTLP direto).
- **Tempo** — traces.
- **Loki** — logs.
- **Grafana** — painel que cruza os três.

Com OTLP, um único coletor (Grafana Alloy ou OpenTelemetry Collector) recebe métricas, traces e logs no mesmo protocolo e distribui pro backend. Antes, cada sinal usava um formato e um collector diferente.

## Estrutura

```
src/main/java/com/example/books/
├── BooksApplication.java
└── book/
    ├── Book.java
    └── BookService.java
src/main/resources/
└── application.yaml
```
