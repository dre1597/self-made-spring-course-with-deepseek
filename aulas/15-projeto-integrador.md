# Aula 15 — Projeto integrador

Objetivo: juntar as decisões do curso em dois processos pequenos e coerentes: publicar um imóvel e agendar visita; abrir uma manutenção e encaminhar para um prestador.

## O projeto

O projeto não tenta construir uma imobiliária inteira. Ele implementa quatro fluxos verticais:

- proprietário publica um imóvel e interessado agenda visita;
- inquilino abre manutenção e o serviço de campo agenda prestador;
- assistente responde sobre imóvel e regras usando dados do sistema;
- job gera o resumo mensal de contratos e chamados.

O projeto principal se chama `property-platform`. O serviço `maintenance-service` fica separado para exercitar gRPC, mensageria, resiliência e deploy independente. A aula deixa fora o CRUD completo de usuários, pagamentos, assinatura digital e integrações com portais externos.

## Base do projeto

O integrador usa dois processos próprios:

- `property-platform`, pacote `com.example.propertyplatform`, API HTTP na porta `8080`;
- `maintenance-service`, pacote `com.example.maintenanceservice`, gRPC na porta `9090`.

O PostgreSQL representa o banco de produção da plataforma. Para a execução local desta aula, o `property-platform` usa H2 em memória; RabbitMQ continua sendo um processo externo porque o fluxo de manutenção precisa exercitar mensageria real. O Ollama também precisa estar disponível quando o fluxo do assistente for executado.

### `property-platform`

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-validation")
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
implementation("org.springframework.boot:spring-boot-starter-flyway")
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("org.springframework.boot:spring-boot-starter-aspectj")
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("org.springframework.boot:spring-boot-starter-batch")
implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")
implementation("org.springframework.boot:spring-boot-starter-amqp")
implementation("org.springframework.boot:spring-boot-starter-grpc-client")
implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
implementation("org.springframework.ai:spring-ai-starter-model-ollama")
implementation("org.springframework.ai:spring-ai-vector-store")
implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")
implementation("com.example:property-contract:1.0.0")
runtimeOnly("org.postgresql:postgresql")
runtimeOnly("com.h2database:h2")
```

O `webmvc` expõe a API; `validation` valida os requests; JPA e Flyway cuidam do banco; Security protege as operações; Batch gera o relatório; AMQP publica manutenção; o starter gRPC chama o serviço de campo; Spring AI atende o assistente; Actuator deixa o processo observável. H2 mantém o desenvolvimento local simples; PostgreSQL é o banco de produção.

Mantenha este arquivo completo em `property-platform/src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: property-platform
  datasource:
    url: jdbc:h2:mem:property-platform;DB_CLOSE_DELAY=-1
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
  batch:
    jdbc:
      initialize-schema: embedded
    job:
      enabled: false
  rabbitmq:
    host: localhost
    port: 5672
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${JWT_ISSUER_URI}
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        options:
          model: llama3.1
      embedding:
        options:
          model: nomic-embed-text
server:
  port: 8080
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
```

### `maintenance-service`

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-grpc-server")
implementation("org.springframework.boot:spring-boot-starter-amqp")
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")
implementation("com.example:property-contract:1.0.0")
```

```java
package com.example.maintenanceservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MaintenanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MaintenanceApplication.class, args);
    }
}
```

Mantenha este arquivo completo em `maintenance-service/src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: maintenance-service
  rabbitmq:
    host: localhost
    port: 5672
  grpc:
    server:
      port: 9090
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
```

Os dois projetos dependem do artefato protobuf compartilhado que contém o contrato `FieldOperations`. O `property-platform` usa o cliente gRPC; o `maintenance-service` usa o servidor gRPC. Os comandos sempre informam em qual diretório cada processo deve ser iniciado.

Para validar os módulos do monólito e registrar publicações depois do commit:

```kotlin
implementation(platform("org.springframework.modulith:spring-modulith-bom:2.1.1"))
implementation("org.springframework.modulith:spring-modulith-starter-core")
implementation("org.springframework.modulith:spring-modulith-starter-jpa")
testImplementation("org.springframework.modulith:spring-modulith-starter-test")
```

A aplicação principal:

```java
package com.example.propertyplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
public class PropertyPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropertyPlatformApplication.class, args);
    }
}
```

O `@Modulithic` verifica os limites dos módulos do monólito. O sistema começa como uma aplicação e extrai apenas o serviço de manutenção, que tem motivo operacional para receber deploy próprio.

Para iniciar a plataforma localmente, suba RabbitMQ, Ollama e os dois processos. O fluxo HTTP usa `property-platform:8080`; o fluxo gRPC usa `maintenance-service:9090`.

## Fluxo 1 — Imóvel e visita

O primeiro fluxo prova o núcleo do produto: um proprietário publica um imóvel, e um interessado agenda uma visita. A aplicação não precisa de telas ou de CRUD de usuário para demonstrar isso; os requests usam ids de usuários já semeados.

Crie `property-platform/src/main/resources/db/migration/V1__create_property_tables.sql` com as tabelas necessárias ao fluxo:

```sql
CREATE TABLE properties (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    address VARCHAR(255) NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE visits (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    property_id BIGINT NOT NULL REFERENCES properties(id),
    prospect_id BIGINT NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL
);
```

As classes do módulo `property` mapeiam a tabela e representam o estado usado pelo serviço:

```java
package com.example.propertyplatform.property;

public enum PropertyPurpose {
    RENT,
    SALE
}
```

```java
package com.example.propertyplatform.property;

public enum PropertyStatus {
    AVAILABLE,
    RESERVED
}
```

```java
package com.example.propertyplatform.property;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "properties")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long ownerId;
    private String address;
    private PropertyPurpose purpose;
    private PropertyStatus status;
    private Instant createdAt;

    protected Property() {
    }

    public Property(Long ownerId, String address, PropertyPurpose purpose, PropertyStatus status) {
        this.ownerId = ownerId;
        this.address = address;
        this.purpose = purpose;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public PropertyStatus getStatus() {
        return status;
    }
}
```

```java
package com.example.propertyplatform.property;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, Long> {
}
```

Os módulos `property` e `visit` não compartilham classes internas. O serviço publica eventos quando uma operação muda o estado:

```java
package com.example.propertyplatform.property;

import java.time.Instant;

public record PropertyListed(Long propertyId, Long ownerId, String purpose, Instant occurredAt) {
}
```

```java
package com.example.propertyplatform.property;

import java.time.Instant;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyService {

    private final PropertyRepository properties;
    private final ApplicationEventPublisher events;

    public PropertyService(PropertyRepository properties, ApplicationEventPublisher events) {
        this.properties = properties;
        this.events = events;
    }

    @Transactional
    public Property list(Long ownerId, ListPropertyRequest request) {
        Property property = properties.save(
                new Property(ownerId, request.address(), request.purpose(), PropertyStatus.AVAILABLE));
        events.publishEvent(new PropertyListed(property.getId(), ownerId, request.purpose(), Instant.now()));
        return property;
    }

    public record ListPropertyRequest(String address, PropertyPurpose purpose) {
    }
}
```

O endpoint permite testar a publicação sem conhecer o repositório:

```java
@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Property list(@AuthenticationPrincipal Jwt jwt,
                         @RequestBody @Valid PropertyService.ListPropertyRequest request) {
        return propertyService.list(Long.valueOf(jwt.getSubject()), request);
    }
}
```

O fluxo de visita usa o mesmo limite: valida disponibilidade, grava a visita e publica `VisitScheduled`. O listener de notificações reage depois do commit, sem fazer o proprietário esperar por e-mail ou mensagem:

```java
package com.example.propertyplatform.visit;

public enum VisitStatus {
    SCHEDULED,
    CANCELLED
}
```

```java
package com.example.propertyplatform.visit;

import java.time.Instant;

public record VisitScheduled(Long visitId, Long propertyId, Long prospectId, Instant scheduledAt) {
}
```

```java
package com.example.propertyplatform.visit;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "visits")
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long propertyId;
    private Long prospectId;
    private Instant scheduledAt;
    private VisitStatus status;

    protected Visit() {
    }

    public Visit(Long propertyId, Long prospectId, Instant scheduledAt) {
        this.propertyId = propertyId;
        this.prospectId = prospectId;
        this.scheduledAt = scheduledAt;
        this.status = VisitStatus.SCHEDULED;
    }

    public Long getId() {
        return id;
    }
}
```

```java
package com.example.propertyplatform.visit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitRepository extends JpaRepository<Visit, Long> {
}
```

```java
package com.example.propertyplatform.visit;

import java.time.Instant;

import com.example.propertyplatform.property.Property;
import com.example.propertyplatform.property.PropertyRepository;
import com.example.propertyplatform.property.PropertyStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitService {

    private final PropertyRepository properties;
    private final VisitRepository visits;
    private final ApplicationEventPublisher events;

    public VisitService(PropertyRepository properties,
                        VisitRepository visits,
                        ApplicationEventPublisher events) {
        this.properties = properties;
        this.visits = visits;
        this.events = events;
    }

    @Transactional
    public Visit schedule(Long prospectId, ScheduleVisitRequest request) {
        Property property = properties.findById(request.propertyId())
                .orElseThrow(() -> new IllegalArgumentException("Imóvel não encontrado"));
        if (property.getStatus() != PropertyStatus.AVAILABLE) {
            throw new IllegalStateException("Imóvel indisponível para visita");
        }
        Visit visit = visits.save(new Visit(property.getId(), prospectId, request.scheduledAt()));
        events.publishEvent(new VisitScheduled(visit.getId(), property.getId(), prospectId, request.scheduledAt()));
        return visit;
    }

    public record ScheduleVisitRequest(Long propertyId, Instant scheduledAt) {
    }
}
```

```java
package com.example.propertyplatform.visit;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visits")
public class VisitController {

    private final VisitService visitService;

    public VisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Visit schedule(@AuthenticationPrincipal Jwt jwt,
                          @RequestBody VisitService.ScheduleVisitRequest request) {
        return visitService.schedule(Long.valueOf(jwt.getSubject()), request);
    }
}
```

```java
@Component
public class VisitNotificationListener {

    @ApplicationModuleListener
    public void on(VisitScheduled event) {
        // envia a confirmação para proprietário e interessado
    }
}
```

Teste manual do fluxo:

```http
### Proprietário publica imóvel
POST http://localhost:8080/api/properties
Authorization: Bearer <owner-token>
Content-Type: application/json

{
  "address": "Rua das Laranjeiras, 100",
  "purpose": "RENT"
}

### Interessado agenda visita
POST http://localhost:8080/api/visits
Authorization: Bearer <prospect-token>
Content-Type: application/json

{
  "propertyId": 1,
  "scheduledAt": "2026-10-03T14:00:00Z"
}
```

## Fluxo 2 — Manutenção

O inquilino abre um chamado. A plataforma valida o contrato, grava o pedido e publica `MaintenanceRequested` em RabbitMQ. O serviço separado recebe o evento e consulta um prestador por gRPC.

Dependências específicas da integração:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-amqp")
implementation("org.springframework.boot:spring-boot-starter-grpc-client")
implementation("org.springframework.boot:spring-boot-starter-aspectj")
implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")
implementation("com.example:field-operations-contract:1.0.0")
```

O `maintenance-service` também declara `implementation("com.example:field-operations-contract:1.0.0")`. Publique esse artefato antes de iniciar os dois projetos. O RabbitMQ fica entre a plataforma e o serviço; o gRPC atende a consulta síncrona de prestador.

O evento carrega ids e dados necessários, não entidades JPA. O record fica num pequeno artefato de contratos de mensageria usado pelos dois processos:

```java
package com.example.propertycontract.maintenance;

public record MaintenanceRequested(Long requestId, Long propertyId, String category) {
}
```

O processo da plataforma publica esse evento numa exchange conhecida:

```java
package com.example.propertyplatform.maintenance;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MaintenanceMessagingConfiguration {

    @Bean
    DirectExchange maintenanceExchange() {
        return new DirectExchange("maintenance.exchange");
    }

    @Bean
    Queue maintenanceQueue() {
        return new Queue("maintenance.requests");
    }

    @Bean
    Binding maintenanceBinding(Queue maintenanceQueue, DirectExchange maintenanceExchange) {
        return BindingBuilder.bind(maintenanceQueue)
                .to(maintenanceExchange)
                .with("maintenance.requested");
    }

    @Bean
    Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

```java
package com.example.propertyplatform.maintenance;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class MaintenanceService {

    private final RabbitTemplate rabbitTemplate;

    public MaintenanceService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void request(MaintenanceRequested maintenance) {
        rabbitTemplate.convertAndSend(
                "maintenance.exchange",
                "maintenance.requested",
                maintenance);
    }
}
```

Exponha o gatilho HTTP que cria o evento:

```java
package com.example.propertyplatform.maintenance;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenance")
public class MaintenanceController {

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void request(@RequestBody MaintenanceRequest request) {
        maintenanceService.request(
                new MaintenanceRequested(request.requestId(), request.propertyId(), request.category()));
    }

    public record MaintenanceRequest(Long requestId, Long propertyId, String category) {
    }
}
```

O consumidor encaminha a solicitação ao serviço de campo. Se o serviço estiver fora, o circuit breaker devolve `PENDING_ASSIGNMENT` e a mensagem pode ser reprocessada:

```java
package com.example.maintenanceservice;

import com.example.fieldoperationscontract.api.FieldOperationsGrpc;
import com.example.fieldoperationscontract.api.TechnicianRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

@Service
public class MaintenanceAssignmentService {

    private final FieldOperationsGrpc.FieldOperationsBlockingStub fieldOperations;

    public MaintenanceAssignmentService(FieldOperationsGrpc.FieldOperationsBlockingStub fieldOperations) {
        this.fieldOperations = fieldOperations;
    }

    @CircuitBreaker(name = "field-operations", fallbackMethod = "pendingAssignment")
    public Assignment assign(MaintenanceRequested request) {
        var technician = fieldOperations.findAvailableTechnician(TechnicianRequest.newBuilder()
                .setPropertyId(request.propertyId())
                .setCategory(request.category())
                .build());
        return new Assignment(technician.getTechnicianId(), technician.getStatus());
    }

    Assignment pendingAssignment(MaintenanceRequested request, Throwable cause) {
        return new Assignment(null, "PENDING_ASSIGNMENT");
    }

    public record Assignment(Long technicianId, String status) {
    }
}
```

O serviço separado consome a fila e só então chama o cliente gRPC:

```java
package com.example.maintenanceservice;

import com.example.propertycontract.maintenance.MaintenanceRequested;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceRequestedListener {

    private final MaintenanceAssignmentService assignmentService;

    public MaintenanceRequestedListener(MaintenanceAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @RabbitListener(queues = "maintenance.requests")
    public void on(MaintenanceRequested request) {
        assignmentService.assign(request);
    }
}
```

O consumidor também registra o conversor JSON para transformar a mensagem na record compartilhada:

```java
package com.example.maintenanceservice;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MaintenanceConsumerConfiguration {

    @Bean
    Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

O contrato gRPC pertence ao serviço de campo e ao cliente como artefato compartilhado:

```protobuf
syntax = "proto3";

option java_package = "com.example.fieldoperationscontract.api";
option java_multiple_files = true;

service FieldOperations {
  rpc FindAvailableTechnician(TechnicianRequest) returns (TechnicianAssignment);
}

message TechnicianRequest {
  int64 property_id = 1;
  string category = 2;
}

message TechnicianAssignment {
  int64 technician_id = 1;
  string status = 2;
}
```

Esse fluxo exercita comunicação entre processos sem transformar cada entidade da plataforma em microserviço. O RabbitMQ desacopla o pedido; o gRPC resolve uma consulta síncrona; o circuit breaker protege a chamada.

## Fluxo 3 — Assistente

O assistente responde sobre um imóvel e suas regras. Ele não altera contrato nem agenda manutenção diretamente.

Dependências da aplicação principal:

```kotlin
implementation("org.springframework.ai:spring-ai-starter-model-ollama")
implementation("org.springframework.ai:spring-ai-vector-store")
```

O `ChatClient` combina três fontes:

- tool local para consultar disponibilidade do imóvel;
- RAG com regulamento e descrição do imóvel;
- memória de conversa para separar as sessões.

```java
package com.example.propertyplatform.assistant;

import java.util.List;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class PropertyTools {

    @Tool(description = "Consulta imóveis disponíveis por finalidade")
    public List<PropertySummary> findAvailableProperties(String purpose) {
        return List.of(new PropertySummary(1L, "RENT", "Rua das Laranjeiras, 100"));
    }

    public record PropertySummary(Long propertyId, String purpose, String address) {
    }
}
```

```java
package com.example.propertyplatform.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class PropertyAssistantService {

    private final ChatClient chatClient;

    public PropertyAssistantService(ChatClient.Builder builder,
                                     PropertyTools propertyTools,
                                     VectorStore vectorStore,
                                     ChatMemory chatMemory) {
        this.chatClient = builder
                .defaultSystem("Você ajuda com imóveis. Use apenas dados fornecidos e diga quando não souber.")
                .defaultTools(propertyTools)
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore).build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public String ask(String conversationId, String question) {
        return chatClient.prompt()
                .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, conversationId))
                .user(question)
                .call()
                .content();
    }
}
```

O endpoint recebe `conversationId` para separar sessões. O RAG indexa somente documentos autorizados ao usuário; permissão não pode ficar a cargo do modelo.

O `VectorStore` do assistente usa os embeddings do Ollama. A indexação deve receber apenas documentos que o usuário pode consultar:

```java
package com.example.propertyplatform.assistant;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PropertyAssistantConfiguration {

    @Bean
    VectorStore propertyVectorStore(EmbeddingModel embeddingModel) {
        var vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        vectorStore.add(java.util.List.of(
                new Document("Animais são permitidos em imóveis com autorização do condomínio.")));
        return vectorStore;
    }
}
```

```java
package com.example.propertyplatform.assistant;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class PropertyAssistantController {

    private final PropertyAssistantService assistantService;

    public PropertyAssistantController(PropertyAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/ask")
    public Answer ask(@RequestBody AskRequest request) {
        return new Answer(assistantService.ask(request.conversationId(), request.question()));
    }

    public record AskRequest(String conversationId, String question) {
    }

    public record Answer(String answer) {
    }
}
```

```http
### Pergunta sobre o imóvel
POST http://localhost:8080/api/assistant/ask
Content-Type: application/json

{
  "conversationId": "prospect-1",
  "question": "O condomínio permite animais e como está o tempo para visitar hoje?"
}
```

## Fluxo 4 — Relatório mensal

O Batch gera um resumo para o proprietário: contratos ativos, chamados abertos e tempo médio de atendimento. O job lê dados já persistidos, calcula o resultado em chunks e grava `owner_monthly_reports`.

```kotlin
implementation("org.springframework.boot:spring-boot-starter-batch")
implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")
implementation("org.springframework.boot:spring-boot-starter-jdbc")
```

O mesmo `JobRepository` registra a execução. Use `month` e `ownerId` como parâmetros: a mesma combinação retoma uma execução interrompida e uma combinação nova cria outro relatório. O job não roda automaticamente na subida:

```yaml
spring:
  batch:
    job:
      enabled: false
```

O `MonthlyReportJobConfiguration` declara o job e o step chunked. O reader consulta contratos e chamados, o processor calcula os indicadores e o writer grava `owner_monthly_reports` com uma operação idempotente. O endpoint dispara a geração e devolve o `executionId` para consulta. Um item inválido deve ser pulado com limite explícito; uma falha de banco deve interromper o chunk e permitir retomada.

O controller usa o mesmo `JobOperator` da Aula 12:

```java
package com.example.propertyplatform.reporting;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class MonthlyReportController {

    private final JobOperator jobOperator;
    private final Job monthlyReportJob;
    private final JobRepository jobRepository;

    public MonthlyReportController(JobOperator jobOperator,
                                   Job monthlyReportJob,
                                   JobRepository jobRepository) {
        this.jobOperator = jobOperator;
        this.monthlyReportJob = monthlyReportJob;
        this.jobRepository = jobRepository;
    }

    @PostMapping("/{ownerId}/{month}")
    public JobStatus generate(@PathVariable Long ownerId, @PathVariable String month)
            throws JobExecutionException {
        JobExecution execution = jobOperator.start(monthlyReportJob,
                new JobParametersBuilder()
                        .addLong("ownerId", ownerId)
                        .addString("month", month)
                        .toJobParameters());
        return new JobStatus(execution.getId(), execution.getStatus().name());
    }

    @GetMapping("/runs/{executionId}")
    public ResponseEntity<JobStatus> status(@PathVariable Long executionId) {
        var execution = jobRepository.getJobExecution(executionId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new JobStatus(execution.getId(), execution.getStatus().name()));
    }

    public record JobStatus(Long executionId, String status) {
    }
}
```

O request retorna `200` com `executionId` e status da execução:

```http
POST http://localhost:8080/api/reports/42/2026-09

> {%
  client.global.set("reportExecutionId", response.body.executionId);
%}

### Consulta a execução devolvida pelo POST
GET http://localhost:8080/api/reports/runs/{{reportExecutionId}}
```

## Segurança

O catálogo pode ser consultado publicamente. Publicar imóvel exige `OWNER`; agendar visita exige usuário autenticado; abrir manutenção exige um inquilino com contrato ativo; relatório exige `OWNER` ou `ADMIN`.

O `property-platform` funciona como Resource Server; ele não emite tokens. Defina `JWT_ISSUER_URI` apontando para o provedor OIDC antes de iniciar o processo e use tokens emitidos por esse provedor nos requests protegidos. A aula não cria um terceiro processo de identidade.

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(HttpMethod.GET, "/api/properties/**").permitAll()
                    .requestMatchers("/api/assistant/**").authenticated()
                    .requestMatchers("/api/reports/**").hasAnyRole("OWNER", "ADMIN")
                    .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    return http.build();
}
```

O controller ainda valida ownership e contrato no serviço. URL protegida não substitui autorização de domínio.

## Observabilidade

Meça os fluxos, não apenas o processo:

- `property.listed.count` mede anúncios publicados;
- `visit.scheduled.count` mede visitas confirmadas;
- `maintenance.assignment.duration` mede o encaminhamento;
- `maintenance.assignment.failures` mostra indisponibilidade do serviço de campo;
- traces conectam HTTP, RabbitMQ e gRPC pelo mesmo correlation id.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  endpoint:
    health:
      probes:
        enabled: true
```

Observe `GET /actuator/health`, consulte as métricas depois de executar os requests e siga o trace do `POST /api/maintenance` até o `maintenance-service`.

## Testes

O integrador não precisa testar cada getter. Teste as fronteiras que sustentam os fluxos:

- `@WebMvcTest` verifica autorização e respostas HTTP;
- `@DataJpaTest` verifica disponibilidade e ownership;
- `@SpringBootTest` verifica publicação de imóvel e evento;
- Testcontainers executa PostgreSQL e RabbitMQ reais;
- teste de contrato verifica o protobuf do serviço de campo;
- teste de arquitetura verifica os módulos do monólito;
- teste de AI usa um modelo fake para não depender de Ollama no CI.

## Deploy

O `property-platform` e o `maintenance-service` geram imagens separadas. O GitLab CI executa os testes, publica as duas imagens no GitLab Container Registry e aplica os manifests no cluster. Cada deployment recebe a imagem pelo SHA do commit; secrets entram como variáveis protegidas do ambiente.

O serviço de manutenção pode escalar independentemente quando chamados aumentarem. Essa é a justificativa do segundo deployable; o restante continua no monólito modular.

## Estrutura

```
property-platform/
├── src/main/java/com/example/propertyplatform/
│   ├── PropertyPlatformApplication.java
│   ├── property/
│   │   ├── Property.java
│   │   ├── PropertyController.java
│   │   ├── PropertyRepository.java
│   │   ├── PropertyService.java
│   │   ├── PropertyPurpose.java
│   │   └── PropertyStatus.java
│   ├── visit/
│   │   ├── Visit.java
│   │   ├── VisitController.java
│   │   ├── VisitRepository.java
│   │   ├── VisitService.java
│   │   └── VisitStatus.java
│   ├── maintenance/
│   │   ├── MaintenanceController.java
│   │   ├── MaintenanceMessagingConfiguration.java
│   │   └── MaintenanceService.java
│   ├── assistant/
│   │   ├── PropertyAssistantConfiguration.java
│   │   ├── PropertyAssistantController.java
│   │   ├── PropertyAssistantService.java
│   │   └── PropertyTools.java
│   ├── reporting/
│   │   ├── MonthlyReportController.java
│   │   └── MonthlyReportJobConfiguration.java
│   └── config/
│       └── SecurityConfiguration.java
├── src/main/resources/
│   ├── application.yaml
│   └── db/migration/
│       └── V1__create_property_tables.sql
└── src/test/java/com/example/property/
    ├── property/
    ├── visit/
    └── architecture/
field-operations-contract/
└── src/main/proto/field-operations.proto
property-contract/
└── src/main/java/com/example/propertycontract/maintenance/MaintenanceRequested.java
maintenance-service/
├── src/main/java/com/example/maintenanceservice/
│   ├── MaintenanceApplication.java
│   ├── MaintenanceAssignmentService.java
│   ├── MaintenanceConsumerConfiguration.java
│   ├── MaintenanceRequestedListener.java
│   └── FieldOperationsGrpcService.java
└── src/main/resources/
    └── application.yaml
```
