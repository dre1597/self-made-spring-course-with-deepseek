# Aula 06 — Concorrência e resiliência

Objetivo: rodar trabalho em virtual threads, assíncrono com `@Async`, e retry/concurrency limit direto no core do Spring 7.

Base do projeto:

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-restclient")
```

O `spring-boot-starter` traz o contexto, o scheduling, o async e a resiliência do core (tudo do Spring Framework 7). O `restclient` cobre o `RestClient`, usado nas seções de retry.

## Virtual threads

Uma linha no `application.yaml`:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Virtual threads são baratas: dá pra ter milhares sem esgotar o sistema. O Boot passa o Tomcat e os executores de tarefa pra virtual threads quando a flag liga. Bloquear numa virtual thread suspende só ela, não a thread do sistema que a carrega.

O ponto de atenção é pinning: quando uma virtual thread bloqueia numa operação `synchronized` ou numa chamada nativa, ela "prende" a carrier thread e o ganho se perde. Pra detectar, rode com `-Djdk.tracePinnedThreads` e veja o log.

## @Async

Métodos assíncronos rodam fora da thread da requisição. Habilite com `@EnableAsync`:

```java
package com.example.books.config;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfiguration {

    @Bean
    ThreadPoolTaskExecutor taskExecutor(TaskDecorator taskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setTaskDecorator(taskDecorator);
        executor.initialize();
        return executor;
    }

    @Bean
    TaskDecorator taskDecorator() {
        return runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    MDC.clear();
                }
            };
        };
    }
}
```

Com virtual threads ligadas, o `@Async` usa o executor virtual do Boot e esse executor próprio não é necessário. O `ThreadPoolTaskExecutor` aqui entra quando você quer um pool limitado. O `TaskDecorator` propaga o MDC pra thread de trabalho: sem ele, o correlation id some na task assíncrona.

O método anotado:

```java
package com.example.books.import;

import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class BookImportService {

    @Async
    public CompletableFuture<Void> importInBackground(String source) {
        process(source);
        return CompletableFuture.completedFuture(null);
    }

    private void process(String source) {
        // trabalho demorado: baixar e gravar livros
    }
}
```

`@Async` retorna `CompletableFuture<Void>` pra você poder aguardar a conclusão. Vale só quando o método é chamado de outro bean; self-invocation não passa pelo proxy.

## @Scheduled

Trabalho agendado roda em horário fixo, sem requisição. `@EnableScheduling` liga o agendador:

```java
package com.example.books.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
```

O método anotado roda na cadência definida:

```java
package com.example.books.report;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OverdueLoanReminder {

    private static final Logger logger = LoggerFactory.getLogger(OverdueLoanReminder.class);

    @Scheduled(cron = "0 0 8 * * MON-FRI")
    public void remind() {
        // varre empréstimos vencidos e notifica
        logger.info("Varredura de empréstimos vencidos às {}", Instant.now());
    }
}
```

Três formas de cadência:

- `fixedDelay = 60000`: 60s depois do fim da execução anterior. Não sobrepõe execuções.
- `fixedRate = 60000`: a cada 60s a partir do início. Pode sobrepor se a execução demorar.
- `cron = "0 0 8 * * MON-FRI"`: segundo, minuto, hora, dia do mês, mês, dia da semana. Dias úteis às 8h.

Com virtual threads ligadas, o agendador usa virtual threads e a sobreposição do `fixedRate` custa pouco. `cron` é o formato expressivo; os dois outros são intervalos simples. Agendamento distribuído, onde só um nó de vários executa a tarefa, não sai daqui: precisa de ShedLock ou de um broker.

## @Retryable

O Spring 7 trouxe retry pro core, sem dependência do Spring Retry. Ative com `@EnableResilientMethods`:

```java
package com.example.books.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

@Configuration
@EnableResilientMethods
public class ResilienceConfiguration {
}
```

Exceção de domínio:

```java
package com.example.books.book;

public class PriceServiceUnavailableException extends RuntimeException {

    public PriceServiceUnavailableException(Long bookId, Throwable cause) {
        super("Serviço de preços indisponível ao buscar o livro " + bookId, cause);
    }
}
```

O serviço que chama o endpoint externo e retenta nas falhas transitórias:

```java
package com.example.books.book;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
public class PriceLookupService {

    private final RestClient restClient;

    public PriceLookupService(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://localhost:9090").build();
    }

    @Retryable(
            includes = PriceServiceUnavailableException.class,
            maxRetries = 4,
            delay = 500,
            multiplier = 2,
            maxDelay = 4000)
    public Price findPrice(Long bookId) {
        try {
            return restClient.get()
                    .uri("/prices/{bookId}", bookId)
                    .retrieve()
                    .body(Price.class);
        } catch (ResourceAccessException exception) {
            throw new PriceServiceUnavailableException(bookId, exception);
        }
    }

    public record Price(Long bookId, String currency, double amount) {
    }
}
```

`includes` restringe o retry a exceções transitórias; sem ele, qualquer exceção retenta, incluindo `NullPointerException`. `maxRetries = 4` significa 1 tentativa inicial + 4 retries = 5 execuções. `delay` é o intervalo base em ms; `multiplier = 2` dobra a cada retry (500, 1000, 2000, 4000); `maxDelay` corta o crescimento.

## RetryTemplate

A versão programática, pra blocos de código arbitrários:

```java
package com.example.books.book;

import java.time.Duration;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
public class PaymentRetryService {

    private final RestClient restClient;

    public PaymentRetryService(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://localhost:9091").build();
    }

    public void charge(ChargeRequest request) {
        RetryTemplate retryTemplate = new RetryTemplate(
                RetryPolicy.builder()
                        .includes(ResourceAccessException.class)
                        .maxRetries(3)
                        .delay(Duration.ofSeconds(1))
                        .multiplier(2)
                        .maxDelay(Duration.ofSeconds(5))
                        .build());

        retryTemplate.invoke(() -> {
            restClient.post()
                    .uri("/charges")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    public record ChargeRequest(Long bookId, double amount) {
    }
}
```

`RetryTemplate` é leve e descartável: cria um por operação quando a política muda. `invoke` executa e retenta; se esgotar, propaga a última exceção.

## @ConcurrencyLimit

Protege um recurso de ser acessado por threads demais ao mesmo tempo, como um pool limitado faria. Útil com virtual threads, que não têm teto natural.

```java
package com.example.books.book;

import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;

@Service
public class BookCacheWarmer {

    @ConcurrencyLimit(limit = 2)
    public void warmUp(Long bookId) {
        // leitura pesada que não aguenta muitas threads simultâneas
    }
}
```

Com `limit = 2`, no máximo duas threads entram ao mesmo tempo. A terceira bloqueia até liberar. Pra rejeitar em vez de bloquear:

```java
@ConcurrencyLimit(limit = 2, policy = ConcurrencyLimit.ThrottlePolicy.REJECT)
```

`REJECT` lança `InvocationRejectedException` na chamada excedente, em vez de esperar.

## Estrutura

```
src/main/java/com/example/books/
├── BooksApplication.java
├── book/
│   ├── PriceServiceUnavailableException.java
│   ├── PriceLookupService.java
│   ├── PaymentRetryService.java
│   └── BookCacheWarmer.java
├── import/
│   └── BookImportService.java
├── report/
│   └── OverdueLoanReminder.java
└── config/
    ├── AsyncConfiguration.java
    ├── ResilienceConfiguration.java
    └── SchedulingConfiguration.java
src/main/resources/
└── application.yaml
```
