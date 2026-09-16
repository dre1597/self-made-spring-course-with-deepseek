# Aula 08 — Observabilidade

Objetivo: colocar um serviço real sob observação — health checks, traces, logs correlacionados e métricas de negócio — exportando tudo em OTLP pra um stack Grafana.

Domínio da aula: um serviço de billing. Você cria faturas por API e o foco está no que acontece em volta do serviço — o processo vivo ou não, pronto ou não pra tráfego, latência, faturas criadas. Projeto novo, pacote `com.example.billing` — nada de reaproveitar o projeto da aula anterior.

## Módulo 1 — Actuator, health e probes

**A ideia:** observabilidade começa pelas perguntas mais baratas de responder: o processo tá de pé (`liveness`), tá pronto pra receber tráfego (`readiness`), e as dependências dele tão de pé (`health`). O Actuator expõe essas respostas como endpoints HTTP — endpoints que a plataforma (Kubernetes, hoje ou mais pra frente) consome como contrato.

Novo projeto:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("org.springframework.boot:spring-boot-starter-restclient")
```

O `actuator` traz os endpoints de gestão: health, metrics, info, entre outros. Por HTTP, ele expõe por default só `health` e `info`; o resto fica escondido até você abrir. O `restclient` entra porque o auto-configure que cria o bean `RestClient.Builder` é módulo próprio no Boot 4 — o `webmvc` não puxa. Sem ele, a injeção do builder no `HealthIndicator` abaixo quebra com `NoSuchBeanDefinitionException`.

Configuração (`application.yaml`):

```yaml
spring:
  application:
    name: billing

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
```

`show-details: always` é pra dev — sem ele o `/actuator/health` devolve só `{"status":"UP"}` sem os componentes. `probes.enabled: true` liga os endpoints de `liveness` e `readiness`; em produção eles alimentam o Kubernetes. E `spring.application.name` nomeia o serviço — o mesmo nome que vai aparecer em cada log, trace e métrica exportados.

Os quatro endpoints que importam:

- `/actuator/health` — o agregado: status geral e, com `show-details`, cada componente verificado.
- `/actuator/health/liveness` — o processo tá vivo. Erro aqui dá sinal pro Kubernetes reiniciar o pod.
- `/actuator/health/readiness` — o app tá pronto pra receber requisição. Erro aqui tira o pod do load balancer sem matar nada.
- `/actuator/metrics` — catálogo de métricas; `/actuator/metrics/jvm.memory.used` detalha uma específica.

Um detalhe que mudou no Boot 4: o pacote do health mudou. Antes era `org.springframework.boot.actuate.health`; agora, `org.springframework.boot.health.contributor` — exemplo antigo com import de `actuate.health` vai quebrar de import.

O Boot já checa sozinho os básicos: disco, datasource, bibliotecas de infra. O que ele não sabe é se as dependências do seu domínio estão de pé — o gateway de pagamento externo, por exemplo. Um `HealthIndicator` customizado responde isso, e o componente entra no status agregado junto com os demais:

```java
package com.example.billing.config;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentGatewayHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(PaymentGatewayHealthIndicator.class);

    private final RestClient restClient;

    public PaymentGatewayHealthIndicator(RestClient.Builder restClientBuilder) {
        var requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(1));
        this.restClient = restClientBuilder
                .baseUrl("http://localhost:9999")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Health health() {
        try {
            restClient.get().uri("/").retrieve().toBodilessEntity();
            logger.info("gateway de pagamento respondendo");
            return Health.up().withDetail("gateway", "http://localhost:9999").build();
        } catch (Exception e) {
            logger.warn("gateway de pagamento indisponível", e);
            return Health.down(e).build();
        }
    }
}
```

O HealthIndicator faz um GET com timeout de 1 segundo no gateway e devolve UP ou DOWN, com o erro embutido na resposta quando cai. O nome do componente derivado da classe fica `paymentGateway`, e é como ele aparece no JSON do `/actuator/health`. O log é seu de propósito: cada verificação registra o resultado, e toda linha sai com o `[app,traceId,spanId]` do request que disparou o health.

**Pra testar este módulo:** só o app. Rode e teste no `requests.http`:

```http
### Health consolidado — paymentGateway deve estar DOWN
GET http://localhost:8080/actuator/health

### Liveness — o processo tá vivo?
GET http://localhost:8080/actuator/health/liveness

### Readiness — tá pronto pra tráfego?
GET http://localhost:8080/actuator/health/readiness

### Catálogo de métricas
GET http://localhost:8080/actuator/metrics
```

No `/actuator/health`, `paymentGateway` está DOWN (porta 9999 sem processo ouvindo), com o erro mostrado no detail — e os componentes built-in UP. Pra ver virar UP, suba um servidor HTTP rústico na porta: `python3 -m http.server 9999` (o probe bate na raiz, que responde 200) e chame o health de novo — `paymentGateway` vira UP no mesmo JSON.

## Infra de teste — stack LGTM

OTLP precisa de um consumidor. O que simplifica é um container único da Grafana que junta os três backends e o painel: Prometheus (métricas), Tempo (traces), Loki (logs) e o Grafana que cruza os três.

`docker-compose.yml`:

```yaml
services:
  otel-lgtm:
    image: grafana/otel-lgtm:latest
    ports:
      - "3000:3000"
      - "4317:4317"
      - "4318:4318"
```

Portas: 4318 (HTTP OTLP) e 4317 (gRPC OTLP) recebem o que a aplicação exporta; 3000 é a console do Grafana.

```bash
docker compose up -d
```

Console em `http://localhost:3000` (login `admin`/`admin` na primeira vez).

## Módulo 2 — Traces e logs em OTLP

**A ideia:** o próprio Boot instrumenta pra você: cada request HTTP vira um *trace* com spans (servlet, filtro, controller), e cada log vira uma linha ligada ao trace em curso. Você não escreve instrumentação de controller nem de logger — o starter exporta tudo pra onde você apontar.

Dependência da seção:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
```

O starter une três camadas: Micrometer (a facade de observabilidade que o Spring usa), o OpenTelemetry SDK e os exporters OTLP de métricas e traces. No Boot 3, cada backend tinha seu registry/bridge; agora, um formato único: OTLP — métricas, traces e logs saem pelo mesmo caminho HTTP.

O YAML completo (módulo 1 + as chaves novas de trace/log):

```yaml
spring:
  application:
    name: billing

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  tracing:
    sampling:
      probability: 1.0
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

Os dois endpoints apontam pro mesmo container OTLP. Em dev, `probability: 1.0` amostra 100% dos traces — em produção, baixe pra 0.1 (só 10% saem, e o custo de armazenamento acompanha).

Nada de appender externo: o export de logs via OTLP é auto-configurado pelo próprio starter (`management.opentelemetry.logging.export.otlp.*`) — no Boot 3, isso exigia um logback appender por fora. E a correlação no console vem de graça com tracing no classpath — o log carregando o traceId do trace em andamento:

```
2026-09-16T10:34:56.123-03:00  WARN [billing,12f8ab3d9c0a4b7e,24d1e7c0b5a9f3e2] 67681 --- [nio-8080-exec-1] c.e.b.c.PaymentGatewayHealthIndicator : gateway de pagamento indisponível
```

O segundo campo entre colchetes é `traceId`, o terceiro é `spanId`. Com eles, você sobe do log pro trace no backend (ou desce do trace pro log).

**Pra testar este módulo:** stack de pé (`docker compose up -d`), app rodando. Dispara o health — ele cai no gateway offline e loga o aviso:

```http
GET http://localhost:8080/actuator/health
```

No console do app, a linha do `PaymentGatewayHealthIndicator` vem com `[billing,traceId,spanId]` entre colchetes. Anota o traceId do meio (32 caracteres hex).

O Grafana, passo a passo:

1. Abre `http://localhost:3000`. Primeiro login: `admin`/`admin`; se pedir pra trocar senha, pode clicar em "Skip".
2. Menu lateral → **Explore** (ícone de bússola).
3. No seletor de datasource no topo, escolhe **Tempo** — o container LGTM já chega com Loki, Tempo e Prometheus configurados.
4. O editor de query é TraceQL. Clica em **click to add resource** → `service.name` → operador `=` → valor `billing` → **Run query**.
5. A lista traz os traces; expande o mais recente — é o `GET /actuator/health`. Na waterfall aparecem os spans de infra criados sozinhos pelo Boot: o span do servlet (duração do request) e, se você expandir, os eventos do health. No topo, o `Trace ID` — confere que bate com o traceId do console.
6. Troca o datasource pro **Loki**. Clica em **Logs** (o label browser) → adiciona `service_name` = `billing` → **Show logs**. As linhas do app aparecem; a do gateway indisponível é a do teu request, com o traceId nos campos da linha.
7. A linha de log traz o `traceID` clicável (no detalhe da linha, em "Search by traceID") → clique abre o trace correspondente direto no Tempo. Se a tua versão não linkar, cola o traceId na query do Tempo.

O ponto da demonstração: você saiu de um log e chegou no trace dele sem grep, sem timestamp, sem cruzar nada na mão — o traceId faz a ponte.

## Módulo 3 — Métricas OTLP e métrica de negócio

**A ideia:** as métricas de infra (JVM, HTTP server, pool) o Boot coleta sozinho. O que não aparece é o seu domínio: faturas criadas, valores faturados, falhas de processamento — a métrica de negócio não existe até você criar com `MeterRegistry`. O custo é baixo: um Counter e uma linha no código de serviço.

Nenhuma dependência nova — o starter da seção anterior já traz o `micrometer-registry-otlp`. Falta apontar o destino (a chave nova é `otlp.metrics`; o resto continua igual):

```yaml
spring:
  application:
    name: billing

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
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

As métricas de infra o Boot já coleta e exporta sozinho: JVM, HTTP server, pool — tudo chega no backend sem código seu.

Boot já tem prontos JVM, HTTP server, banco, cache — tudo visível no `/actuator/metrics` sem uma linha de código. Métrica de negócio, um Counter:

```java
package com.example.billing.invoice;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    private final Counter createdInvoices;

    public InvoiceService(MeterRegistry meterRegistry) {
        this.createdInvoices = Counter.builder("billing.invoices.created")
                .description("Faturas criadas")
                .register(meterRegistry);
    }

    public void invoiceCreated() {
        createdInvoices.increment();
    }
}
```

E precisa de um endpoint chamável:

```java
package com.example.billing.invoice;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/api/invoices")
    public ResponseEntity<Void> create() {
        invoiceService.invoiceCreated();
        return ResponseEntity.accepted().build();
    }
}
```

Sem persistência nessa aula: o objetivo é a métrica, não o domínio por completo. `create` só registra e devolve `202`.

Os três tipos de métrica que cobrem quase tudo:

- **Counter** — contagem que só cresce (faturas criadas, erros).
- **Timer** — duração (chamada de método, request).
- **Gauge** — valor instantâneo (profundidade da fila, memória).

O backend cuida da escala e da janela: o Prometheus deriva deltas de counters cumulativos sozinho.

**Pra testar este módulo:** rode e bata no endpoint umas vezes:

```http
### Cria uma fatura
POST http://localhost:8080/api/invoices
```

Confira:

```http
### A métrica de negócio no detalhe
GET http://localhost:8080/actuator/metrics/billing.invoices.created
```

O valor incrementou a cada POST. No Grafana, seguindo do Explore do módulo 2:

1. Seletor de datasource → **Prometheus**.
2. Abre direto no modo "Builder"; clica em **Code** (do lado direito do editor) pra digitar a query.
3. Digita `billing_invoices_created_total` → **Run query**. É um número, não um gráfico: OTLP entrega o cumulative counter, e a janela ainda não tem histórico suficiente pra curva — depois de alguns minutos batendo no endpoint, vira linha subindo.
4. Confere o mesmo request instrumentado de graça: `http_server_requests_seconds_count{uri="/api/invoices"}` — o timer do HTTP server que você não escreveu.

## Módulo 4 — Observações com @Observed

**A ideia:** `@Observed` em um método gera **duas pontas do mesmo evento**: um timer de métrica e um span de trace, com o mesmo nome. A duração do método vira métrica; a chamada dentro do request vira span — os dois sinais a partir do mesmo ponto de instrumentação.

Dependência da seção:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-aspectj")
```

`@Observed` é processada via AOP: o weaver de aspectos que o `spring-boot-starter-aspectj` traz intercepta cada chamada do método anotado. E no yaml, o processing de annotations é opt-in (a chave nova é `observations`; o resto continua igual):

```yaml
spring:
  application:
    name: billing

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
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
  observations:
    annotations:
      enabled: true
```

Sem `enabled: true`, `@Observed` não processa nada — fica uma annotation silenciosa. O weaver e a flag precisam estar juntos.

Código:

```java
package com.example.billing.invoice;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;

import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    private final Counter createdInvoices;

    public InvoiceService(MeterRegistry meterRegistry) {
        this.createdInvoices = Counter.builder("billing.invoices.created")
                .description("Faturas criadas")
                .register(meterRegistry);
    }

    @Observed(name = "billing.create-invoice")
    public void invoiceCreated() {
        createdInvoices.increment();
    }
}
```

Cada chamada vira um timer `billing.create-invoice` e um span de trace com o mesmo nome. Alternativa manual, sem annotation: injete `ObservationRegistry` e faça `Observation.createNotStarted("billing.create-invoice", registry).observe(...)` — a annotation é só sugar pra isso.

Uma pegadinha: as instrumentações automáticas (controller via `http.server.requests`, repos Spring Data) também criam observação — anotar um código já instrumentado produz timer e span duplicados. Ou desabilita a automática (`management.observations.enable.<prefixo>: false`), ou não anota o que já é instrumentado.

**Pra testar este módulo:** chame o POST `/api/invoices` do módulo 3 (as mesmas chamadas do `requests.http`). Confirmações:

```http
### O método virou métrica
GET http://localhost:8080/actuator/metrics/billing.create-invoice
```

No Grafana:

1. Explore → **Tempo**, mesma query do módulo 2 (`service.name` = `billing`) → **Run query**.
2. Abre o trace mais recente (o POST de agora). Na waterfall, além do span do servlet apareceu um filho: `billing.create-invoice` — o método anotado virou span sem você escrever nada.
3. Clica no span filho: a duração dele (o tempo do método) aparece no painel de detalhes, menor que a do pai (o request inteiro).
4. Explore → **Prometheus** → Code → `billing_create_invoice_seconds_count` → Run query: o mesmo `@Observed` do outro lado, agora como métrica de duração — um pedido, um ponto (contado pelo Counter `billing.invoices.created`), o outro duração (o timer). Dois sinais, uma annotation.

## Estrutura

```
src/main/java/com/example/billing/
├── BillingApplication.java
├── config/
│   └── PaymentGatewayHealthIndicator.java
└── invoice/
    ├── InvoiceController.java
    └── InvoiceService.java

src/main/resources/
└── application.yaml

docker-compose.yml   — stack LGTM all-in-one (Loki, Grafana, Tempo, Prometheus)
requests.http        — chamadas de teste
```
