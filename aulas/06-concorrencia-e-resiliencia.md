# Aula 06 — Concorrência e resiliência

Objetivo: rodar trabalho em virtual threads, assíncrono com `@Async`, agendado com `@Scheduled`, e retry/concurrency limit direto no core do Spring 7.

Domínio: controle de estoque de um e-commerce. A API que dispara os exemplos sustenta a aula inteira, então as dependências de web entram na base; cada seção traz as próprias.

Base do projeto:

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
```

O `spring-boot-starter` traz o contexto, o scheduling, o async e a resiliência do core (tudo do Spring Framework 7). O `webmvc` entra aqui porque cada seção ganha um endpoint que dispara o exemplo — sem a API, os serviços da aula ficam órfãos.

A aplicação principal:

```java
package com.example.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class InventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
```

## Virtual threads

Uma linha no `application.yaml`:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Virtual threads são baratas: dá pra ter milhares sem esgotar o sistema. O Boot passa o Tomcat e os executores de tarefa pra virtual threads quando a flag liga. Bloquear numa virtual thread suspende só ela, não a thread do sistema que a carrega. Nos logs da aula, as threads aparecem como `VirtualThread[#44,...]` — é o jeito de confirmar que o código roda em virtual thread.

O ponto de atenção é pinning: quando uma virtual thread bloqueia numa operação `synchronized` ou numa chamada nativa, ela "prende" a carrier thread e o ganho se perde. Pra detectar, rode com `-Djdk.tracePinnedThreads=full` na run configuration da IDE (ou `JAVA_TOOL_OPTIONS=-Djdk.tracePinnedThreads=full` no terminal) e veja o log.

## @Async

Métodos assíncronos rodam fora da thread da requisição. Habilite com `@EnableAsync`:

```java
package com.example.inventory.config;

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

O método anotado importa o catálogo em background:

```java
package com.example.inventory.imports;

import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class InventoryImportService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryImportService.class);

    @Async
    public CompletableFuture<Void> importInBackground(String source) {
        logger.info("Importando {} na thread {}", source, Thread.currentThread());
        process(source);
        return CompletableFuture.completedFuture(null);
    }

    private void process(String source) {
        // trabalho demorado: baixar e gravar o catálogo do fornecedor
    }
}
```

`@Async` retorna `CompletableFuture<Void>` pra você poder aguardar a conclusão. Vale só quando o método é chamado de outro bean; self-invocation não passa pelo proxy. O log da thread é o que mostra o efeito: o endpoint do teste volta `202 Accepted` na hora, e o `Importando...` aparece depois, numa thread diferente.

## @Scheduled

Trabalho agendado roda em horário fixo, sem requisição. `@EnableScheduling` liga o agendador:

```java
package com.example.inventory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfiguration {
}
```

O método anotado roda na cadência definida:

```java
package com.example.inventory.stock;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LowStockScanner {

    private static final Logger logger = LoggerFactory.getLogger(LowStockScanner.class);

    @Scheduled(cron = "0 0 8 * * MON-FRI")
    public void scan() {
        // varre produtos com estoque baixo e dispara reposição
        logger.info("Varredura de estoque baixo às {}", Instant.now());
    }
}
```

Três formas de cadência:

- `fixedDelay = 60000`: 60s depois do fim da execução anterior. Não sobrepõe execuções.
- `fixedRate = 60000`: a cada 60s a partir do início. Pode sobrepor se a execução demorar.
- `cron = "0 0 8 * * MON-FRI"`: segundo, minuto, hora, dia do mês, mês, dia da semana. Dias úteis às 8h.

Com virtual threads ligadas, o agendador usa virtual threads e a sobreposição do `fixedRate` custa pouco. `cron` é o formato expressivo; os dois outros são intervalos simples. Pra ver funcionando sem esperar as 8h, troque o cron pra `* * * * * *` (a cada segundo) e acompanhe o log. Agendamento distribuído, onde só um nó de vários executa a tarefa, não sai daqui: precisa de ShedLock ou de um broker.

## @Retryable

Esta seção precisa do `RestClient`:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-restclient")
```

O Spring 7 trouxe retry pro core, sem dependência do Spring Retry. Ative com `@EnableResilientMethods` — essa config liga os métodos resilientes do core, e o `@ConcurrencyLimit` da seção mais adiante também depende dela:

```java
package com.example.inventory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

@Configuration
@EnableResilientMethods
public class ResilienceConfiguration {
}
```

Exceção de domínio:

```java
package com.example.inventory.vendor;

public class VendorUnavailableException extends RuntimeException {

    public VendorUnavailableException(Long productId, Throwable cause) {
        super("Fornecedor indisponível ao buscar o preço do produto " + productId, cause);
    }
}
```

O serviço que chama o fornecedor externo e retenta nas falhas transitórias. Falha transitória não é só conexão caindo: 5xx conta, então o catch pega `ResourceAccessException` (sem conexão) e `HttpServerErrorException` (resposta 5xx):

```java
package com.example.inventory.vendor;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
public class PriceLookupService {

    private final RestClient restClient;

    public PriceLookupService(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://localhost:8080").build();
    }

    @Retryable(
            includes = VendorUnavailableException.class,
            maxRetries = 4,
            delay = 500,
            multiplier = 2,
            maxDelay = 4000)
    public Price findPrice(Long productId) {
        try {
            return restClient.get()
                    .uri("/api/vendor/prices/{productId}", productId)
                    .retrieve()
                    .body(Price.class);
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            throw new VendorUnavailableException(productId, exception);
        }
    }
}
```

`Price` é o DTO que o fornecedor devolve:

```java
package com.example.inventory.vendor;

public record Price(Long productId, String currency, double amount) {
}
```

`includes` restringe o retry a exceções transitórias; sem ele, qualquer exceção retenta, incluindo `NullPointerException`. `maxRetries = 4` significa 1 tentativa inicial + 4 retries = 5 execuções. `delay` é o intervalo base em ms; `multiplier = 2` dobra a cada retry (500, 1000, 2000, 4000); `maxDelay` corta o crescimento.

O teste de retry precisa de um fornecedor que falha e recupera. O stub abaixo é esse fornecedor: responde `503` nas duas primeiras chamadas por produto e `200` a partir da terceira. Ele mora no próprio app, só pra demonstração:

```java
package com.example.inventory.vendor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VendorStubController {

    private final Map<Long, AtomicInteger> priceAttempts = new ConcurrentHashMap<>();

    @GetMapping("/api/vendor/prices/{productId}")
    public ResponseEntity<Price> price(@PathVariable Long productId) {
        int attempt = priceAttempts.computeIfAbsent(productId, id -> new AtomicInteger()).incrementAndGet();
        if (attempt <= 2) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(new Price(productId, "BRL", 29.90));
    }
}
```

## RetryTemplate

A versão programática, pra blocos de código arbitrários. Não precisa de proxy nem de anotação:

```java
package com.example.inventory.vendor;

import java.time.Duration;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Service
public class SupplierOrderService {

    private final RestClient restClient;

    public SupplierOrderService(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://localhost:8080").build();
    }

    public void placeOrder(OrderRequest request) {
        RetryTemplate retryTemplate = new RetryTemplate(
                RetryPolicy.builder()
                        .includes(ResourceAccessException.class, HttpServerErrorException.class)
                        .maxRetries(3)
                        .delay(Duration.ofSeconds(1))
                        .multiplier(2)
                        .maxDelay(Duration.ofSeconds(5))
                        .build());

        retryTemplate.invoke(() -> {
            restClient.post()
                    .uri("/api/vendor/orders")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    public record OrderRequest(Long productId, int quantity) {
    }
}
```

`RetryTemplate` é leve e descartável: cria um por operação quando a política muda. `invoke` executa e retenta; se esgotar, propaga a última exceção. Aqui a política inclui as duas exceções de rede direto (`ResourceAccessException` e `HttpServerErrorException`), sem exceção de domínio no meio.

O stub ganha o endpoint de pedido, com a mesma regra (503 nas duas primeiras chamadas). Adicione no `VendorStubController`, importando `PostMapping`:

```java
private final AtomicInteger orderAttempts = new AtomicInteger();

@PostMapping("/api/vendor/orders")
public ResponseEntity<Void> orders() {
    if (orderAttempts.incrementAndGet() <= 2) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
    return ResponseEntity.accepted().build();
}
```

## @ConcurrencyLimit

Protege um recurso de ser acessado por threads demais ao mesmo tempo, como um pool limitado faria. Útil com virtual threads, que não têm teto natural. O `@EnableResilientMethods` da seção do retry cobre este método também:

```java
package com.example.inventory.catalog;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.stereotype.Service;

@Service
public class CatalogSnapshotService {

    private static final Logger logger = LoggerFactory.getLogger(CatalogSnapshotService.class);

    @ConcurrencyLimit(limit = 2)
    public void rebuild(Long productId) {
        logger.info("Snapshot do catálogo iniciado pra {} às {}", productId, Instant.now());
        // leitura pesada que não aguenta muitas threads simultâneas
        try {
            Thread.sleep(2000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        logger.info("Snapshot do catálogo concluído pra {} às {}", productId, Instant.now());
    }
}
```

O `Thread.sleep` segura a thread pra você conseguir disparar várias chamadas e ver o limite atuando. Com `limit = 2`, no máximo duas threads entram ao mesmo tempo. A terceira bloqueia até uma liberar — no log, nunca aparecem mais que dois "iniciado" sem o "concluído" correspondente. Pra rejeitar em vez de bloquear:

```java
@ConcurrencyLimit(limit = 2, policy = ConcurrencyLimit.ThrottlePolicy.REJECT)
```

`REJECT` lança `InvocationRejectedException` na chamada excedente, em vez de esperar.

## Aula inteira testável (ponta a ponta)

Cada seção depende de um endpoint pra disparar o exemplo. Este controller injeta os quatro serviços e expõe um endpoint por seção:

```java
package com.example.inventory.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.catalog.CatalogSnapshotService;
import com.example.inventory.imports.InventoryImportService;
import com.example.inventory.vendor.Price;
import com.example.inventory.vendor.PriceLookupService;
import com.example.inventory.vendor.SupplierOrderService;

@RestController
@RequestMapping("/api")
public class InventoryController {

    private final InventoryImportService importService;
    private final PriceLookupService priceLookupService;
    private final SupplierOrderService supplierOrderService;
    private final CatalogSnapshotService snapshotService;

    public InventoryController(InventoryImportService importService,
                               PriceLookupService priceLookupService,
                               SupplierOrderService supplierOrderService,
                               CatalogSnapshotService snapshotService) {
        this.importService = importService;
        this.priceLookupService = priceLookupService;
        this.supplierOrderService = supplierOrderService;
        this.snapshotService = snapshotService;
    }

    @PostMapping("/inventory/import")
    public ResponseEntity<Void> importCatalog(@RequestParam String source) {
        importService.importInBackground(source);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/prices/{productId}")
    public Price price(@PathVariable Long productId) {
        return priceLookupService.findPrice(productId);
    }

    @PostMapping("/supplier-orders")
    public ResponseEntity<Void> placeOrder(@RequestBody SupplierOrderService.OrderRequest request) {
        supplierOrderService.placeOrder(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/catalog/snapshot/{productId}")
    public ResponseEntity<Void> rebuildSnapshot(@PathVariable Long productId) {
        snapshotService.rebuild(productId);
        return ResponseEntity.accepted().build();
    }
}
```

Os testes manuais rodam da própria IDE, num `requests.http` na raiz do projeto:

```http
### Importação de catálogo em background (@Async)
POST http://localhost:8080/api/inventory/import?source=fornecedor-1

### Preço de produto com retry (@Retryable)
GET http://localhost:8080/api/prices/1

### Pedido de reposição com retry programático (RetryTemplate)
POST http://localhost:8080/api/supplier-orders
Content-Type: application/json

{
  "productId": 2,
  "quantity": 10
}

### Snapshot com limite de concorrência (@ConcurrencyLimit)
POST http://localhost:8080/api/catalog/snapshot/3
```

O que observar em cada disparo:

- **Import**: responde `202 Accepted` na hora; no log, o `Importando fornecedor-1 na thread VirtualThread[...]` aparece depois, em outra thread.
- **Preço**: a primeira chamada demora ~1,5s (500ms + 1000ms dos delays que o stub faz queimar antes de responder 200) e devolve `{"productId":1,"currency":"BRL","amount":29.9}`. Rodar de novo responde na hora: o contador do stub já passou de 2, não há falha pra retentar.
- **Pedido**: `202 Accepted` depois de ~3s (o `RetryTemplate` espera 1s, depois 2s, e a terceira chamada passa).
- **Snapshot**: duplique a linha do snapshot 3 vezes no `requests.http` e rode as três seguidas — os logs mostram no máximo dois "iniciado" ao mesmo tempo; o terceiro só inicia quando um conclui (2s depois).

## Estrutura

```
src/main/java/com/example/inventory/
├── InventoryApplication.java
├── web/
│   └── InventoryController.java
├── imports/
│   └── InventoryImportService.java
├── stock/
│   └── LowStockScanner.java
├── vendor/
│   ├── Price.java
│   ├── VendorUnavailableException.java
│   ├── PriceLookupService.java
│   ├── SupplierOrderService.java
│   └── VendorStubController.java
├── catalog/
│   └── CatalogSnapshotService.java
└── config/
    ├── AsyncConfiguration.java
    ├── ResilienceConfiguration.java
    └── SchedulingConfiguration.java
src/main/resources/
├── application.yaml
└── requests.http
```