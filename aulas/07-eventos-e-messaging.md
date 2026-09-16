# Aula 07 — Eventos e messaging

Objetivo: publicar eventos de domínio, mandar pra JMS, Kafka e RabbitMQ, e fechar a integração entre módulos com Spring Modulith.

Domínio: pedidos de uma loja online.

Os módulos são independentes e você monta um por vez: cada um termina com o projeto compilando e um teste pra rodar. O `requests.http` na raiz vai crescendo a cada módulo — cada módulo mostra o request a adicionar.

## Base do projeto

O que sustenta a aula inteira entra aqui; cada módulo de broker traz o próprio starter.

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
runtimeOnly("com.h2database:h2")
```

O `spring-boot-starter` traz o contexto e o suporte a eventos (`ApplicationEventPublisher`, `@EventListener`). O `webmvc` sustenta a API de pedidos. O `data-jpa` com o `h2` bancam o `OrderRepository` e as transações.

A aplicação principal:

```java
package com.example.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
```

## Módulo 1 — Eventos de domínio

**A ideia:** um evento é "algo que aconteceu" no seu domínio — um pedido foi criado. Quem cria o pedido não precisa saber quem reage a isso (índice de busca, cache, integração). O evento desacopla: o `OrderService` publica e segue a vida; cada listener interessado reage por conta própria. Isso acontece no mesmo processo, em memória, sem broker. É o primeiro degrau antes de sair pra filas externas.

O evento, que só carrega o que aconteceu:

```java
package com.example.shop.order;

public record OrderPlaced(Long orderId, String item, double amount) {
}
```

A entidade e o repositório — o pedido precisa existir pra virar evento:

```java
package com.example.shop.order;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String item;
    private double amount;

    protected Order() {
    }

    public Order(String item, double amount) {
        this.item = item;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public String getItem() {
        return item;
    }

    public double getAmount() {
        return amount;
    }
}
```

```java
package com.example.shop.order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
```

O serviço grava o pedido e publica o evento dentro da mesma transação:

```java
package com.example.shop.order;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(OrderRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Order create(CreateOrderRequest request) {
        Order order = repository.save(new Order(request.item(), request.amount()));
        eventPublisher.publishEvent(new OrderPlaced(order.getId(), order.getItem(), order.getAmount()));
        return order;
    }
}
```

O request:

```java
package com.example.shop.order;

public record CreateOrderRequest(String item, double amount) {
}
```

O listener reage pelo tipo do parâmetro — é o "quem reage" que o produtor desconhece:

```java
package com.example.shop.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedListener {

    private static final Logger logger = LoggerFactory.getLogger(OrderCreatedListener.class);

    @EventListener
    public void on(OrderPlaced event) {
        // análise, cache, log...
        logger.info("Pedido {} registrado no processo", event.orderId());
    }
}
```

O controller entrega o teste deste módulo:

```java
package com.example.shop.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<Order> create(@RequestBody CreateOrderRequest request) {
        Order order = orderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }
}
```

**Pra testar este módulo:** nada externo de pé — H2 em memória. Rode o app e adicione no `requests.http`:

```http
### Cria pedido (evento in-process)
POST http://localhost:8080/api/orders
Content-Type: application/json

{
  "item": "Teclado mecânico",
  "amount": 299.90
}
```

Resposta `201 Created` com o pedido salvo, e no log o `OrderCreatedListener` imprime `Pedido 1 registrado no processo`. O evento foi entregue em memória, na mesma transação que gravou o pedido.

Detalhe de tempo: `@EventListener` roda dentro da transação do publisher. Se você quer reagir **depois** do commit (pra não segurar a transação, por exemplo), troque por `@TransactionalEventListener`:

```java
@TransactionalEventListener
public void on(OrderPlaced event) {
    // aqui a transação já foi commitada
}
```

## Módulo 2 — JMS

**A ideia:** gerar a nota fiscal de um pedido é trabalho que não precisa segurar a resposta da requisição. JMS é o padrão corporativo de **fila ponto a ponto**: uma mensagem vai pra fila, um consumidor tira e processa. O Spring 7 trocou o `JmsTemplate` pelo `JmsClient` — uma API fluente no mesmo espírito do `RestClient`. O melhor pra testar: o Artemis roda **embutido** no processo, então esta seção funciona sem docker nenhum.

Dependência da seção:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-artemis")
runtimeOnly("org.apache.activemq:artemis-jms-server")
```

O starter traz o cliente JMS; o `artemis-jms-server` entra como `runtimeOnly` porque o modo `embedded` precisa do servidor Artemis embutido no classpath. A versão vem do BOM do Boot.

Configuração — uma linha e o broker nasce dentro do app:

```yaml
spring:
  artemis:
    mode: embedded
```

Envio pra fila:

```java
package com.example.shop.order;

import org.springframework.jms.core.JmsClient;
import org.springframework.stereotype.Service;

@Service
public class InvoiceRequestService {

    private final JmsClient jmsClient;

    public InvoiceRequestService(JmsClient jmsClient) {
        this.jmsClient = jmsClient;
    }

    public void request(OrderPlaced event) {
        jmsClient.destination("invoice-generation")
                .send(event);
    }
}
```

Consumo por listener — a fila chama o método quando a mensagem chega:

```java
package com.example.shop.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class InvoiceRequestListener {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceRequestListener.class);

    @JmsListener(destination = "invoice-generation")
    public void on(OrderPlaced event) {
        // gera a nota fiscal do pedido
        logger.info("Nota fiscal gerada pra {}", event);
    }
}
```

Existe também o recebimento síncrono, com timeout — útil quando o consumidor puxa na mão em vez de ser chamado:

```java
Optional<OrderPlaced> received = jmsClient.destination("invoice-generation")
        .withReceiveTimeout(1000)
        .receive(OrderPlaced.class);
```

Tanto o envio quanto o consumo precisam de um conversor que serialize o record. O padrão do JMS (o `SimpleMessageConverter`) só aceita `String`, `byte[]`, `Map` e objetos `Serializable` — e um record Java não é `Serializable`. É por isso que o envio falha com `Cannot convert object of type [OrderPlaced] to JMS message` até você registrar este bean: um conversor Jackson que transforma o record em JSON. O Boot usa um único bean `MessageConverter` tanto pro `JmsClient` quanto pro listener container:

```java
package com.example.shop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.JacksonJsonMessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

@Configuration
public class JmsConfiguration {

    @Bean
    MessageConverter jacksonJmsMessageConverter() {
        var converter = new JacksonJsonMessageConverter();
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");
        return converter;
    }
}
```

O controller entrega o teste deste módulo:

```java
package com.example.shop.order;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InvoiceController {

    private final InvoiceRequestService invoiceService;

    public InvoiceController(InvoiceRequestService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/jms/invoices")
    public ResponseEntity<Void> requestInvoice(@RequestBody OrderPlaced event) {
        invoiceService.request(event);
        return ResponseEntity.accepted().build();
    }
}
```

**Pra testar este módulo:** nada externo — o Artemis é embutido. Rode o app e adicione no `requests.http`:

```http
### Envia nota fiscal pro JMS
POST http://localhost:8080/api/jms/invoices
Content-Type: application/json

{
  "orderId": 1,
  "item": "Teclado mecânico",
  "amount": 299.90
}
```

Resposta `202 Accepted` na hora (a fila aceitou o trabalho), e no log o `InvoiceRequestListener` imprime `Nota fiscal gerada pra OrderPlaced[...]` quando o consumidor tira a mensagem da fila.

## Infra dos brokers (Kafka e RabbitMQ)

Os módulos 3 e 4 usam brokers externos. Um `docker-compose.yml` na raiz do projeto sobe os dois de uma vez (o Kafka agora, o RabbitMQ no módulo 4):

```yaml
services:
  kafka:
    image: apache/kafka:4.0.0
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT

  rabbitmq:
    image: rabbitmq:4-management
    ports:
      - "5672:5672"
      - "15672:15672"
```

`docker compose up -d` sobe os dois; o console do RabbitMQ fica em `http://localhost:15672` (guest/guest). O Kafka não tem console web — a prova de entrega é o log do consumidor.

## Módulo 3 — Kafka

**A ideia:** Kafka é um **log distribuído**, não uma fila clássica. A mensagem vai pro tópico e fica lá; cada consumidor lê do seu próprio ponto (offset), então dá pra voltar e reprocessar histórico, e vários consumidores leem sem tirar a mensagem um do outro. É a escolha quando importa volume, replay e streams. No código: o `KafkaTemplate` publica e o `@KafkaListener` consome.

Dependência da seção:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-kafka")
```

Configuração — aponta o broker e serializa o evento em JSON de ponta a ponta (o tópico carrega o JSON, e o consumidor desserializa de volta pro `OrderPlaced`):

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JacksonJsonSerializer
    consumer:
      group-id: shop
      value-deserializer: org.springframework.kafka.support.serializer.JacksonJsonDeserializer
      properties:
        spring.json.trusted.packages: com.example.shop.*
```

O `spring.json.trusted.packages` libera o deserializer a instanciar classes do pacote do evento; sem isso, o consumo recusa por segurança.

Publicação:

```java
package com.example.shop.order;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderPlacedKafkaPublisher {

    private final KafkaTemplate<String, OrderPlaced> kafkaTemplate;

    public OrderPlacedKafkaPublisher(KafkaTemplate<String, OrderPlaced> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(OrderPlaced event) {
        kafkaTemplate.send("order-placed", event);
    }
}
```

Consumo:

```java
package com.example.shop.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedKafkaListener {

    private static final Logger logger = LoggerFactory.getLogger(OrderPlacedKafkaListener.class);

    @KafkaListener(topics = "order-placed")
    public void on(OrderPlaced event) {
        // processa o evento vindo de outro serviço
        logger.info("Kafka entregou {}", event);
    }
}
```

O controller entrega o teste deste módulo:

```java
package com.example.shop.order;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OrderKafkaController {

    private final OrderPlacedKafkaPublisher publisher;

    public OrderKafkaController(OrderPlacedKafkaPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/kafka/publish")
    public ResponseEntity<Void> publish(@RequestBody OrderPlaced event) {
        publisher.publish(event);
        return ResponseEntity.accepted().build();
    }
}
```

**Pra testar este módulo:** o Kafka precisa estar de pé — `docker compose up -d` (já sobe o RabbitMQ junto, que o módulo 4 usa). Rode o app e adicione no `requests.http`:

```http
### Publica no Kafka
POST http://localhost:8080/api/kafka/publish
Content-Type: application/json

{
  "orderId": 1,
  "item": "Teclado mecânico",
  "amount": 299.90
}
```

Resposta `202 Accepted` na hora. O `OrderPlacedKafkaListener` loga `Kafka entregou OrderPlaced[...]` quando o broker entrega pro consumidor. Se o Kafka não estiver de pé, o log mostra a falha de conexão e o tópico nunca recebe.

## Módulo 4 — RabbitMQ

**A ideia:** RabbitMQ é fila com **roteamento**. A mensagem não vai direto pra fila: vai pra uma **exchange**, e o **binding** decide quais filas recebem, por **routing key**. O produtor conhece só a exchange e a chave — nunca a fila. Essa topologia (exchange, filas, bindings) você declara como beans, e o broker cria tudo quando o app sobe. É a escolha quando o roteamento importa, e a DLQ dá rede de segurança pro que falha.

Dependência da seção:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-amqp")
```

Configuração — aponta o broker do compose:

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
```

A topologia. Repare que a fila `order-created` já nasce apontando pra dead letter exchange — o `order-dlx` é usado logo abaixo, na DLQ:

```java
package com.example.shop.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqTopologyConfiguration {

    @Bean
    MessageConverter jacksonAmqpMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    TopicExchange orderExchange() {
        return new TopicExchange("order-exchange");
    }

    @Bean
    TopicExchange orderDeadLetterExchange() {
        return new TopicExchange("order-dlx");
    }

    @Bean
    Queue orderCreatedQueue() {
        return QueueBuilder.durable("order-created")
                .deadLetterExchange("order-dlx")
                .deadLetterRoutingKey("order.created.dead")
                .build();
    }

    @Bean
    Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderCreatedQueue).to(orderExchange).with("order.created");
    }
}
```

publicação — `convertAndSend` serializa o evento com o conversor acima e manda pra exchange com a routing key `order.created`:

```java
package com.example.shop.order;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderPlacedAmqpPublisher {

    private final RabbitTemplate rabbitTemplate;

    public OrderPlacedAmqpPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(OrderPlaced event) {
        rabbitTemplate.convertAndSend("order-exchange", "order.created", event);
    }
}
```

Consumo — o listener escuta a fila, que a exchange alimenta:

```java
package com.example.shop.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderPlacedAmqpListener {

    private static final Logger logger = LoggerFactory.getLogger(OrderPlacedAmqpListener.class);

    @RabbitListener(queues = "order-created")
    public void on(OrderPlaced event) {
        // processa o evento vindo de outro serviço
        logger.info("RabbitMQ entregou {}", event);
    }
}
```

O controller entrega o teste deste módulo:

```java
package com.example.shop.order;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class OrderAmqpController {

    private final OrderPlacedAmqpPublisher publisher;

    public OrderAmqpController(OrderPlacedAmqpPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/rabbit/publish")
    public ResponseEntity<Void> publish(@RequestBody OrderPlaced event) {
        publisher.publish(event);
        return ResponseEntity.accepted().build();
    }
}
```

**Pra testar este módulo:** o RabbitMQ já está de pé (o compose do módulo 3 subiu os dois). Rode o app e adicione no `requests.http`:

```http
### Publica no RabbitMQ
POST http://localhost:8080/api/rabbit/publish
Content-Type: application/json

{
  "orderId": 1,
  "item": "Teclado mecânico",
  "amount": 299.90
}
```

Resposta `202 Accepted` e o `OrderPlacedAmqpListener` loga `RabbitMQ entregou OrderPlaced[...]`. Dá pra ver a fila `order-created` ganhar a mensagem no console `http://localhost:15672` (guest/guest) → aba Queues.

### Dead Letter Queue

**A ideia:** quando o consumo falha e o retry esgota, a mensagem não pode sumir. A DLQ (dead letter queue) recebe o que não deu, pra você inspecionar, reprocessar com correção ou registrar e descartar. Sem DLQ, a falha vira perda silenciosa ou loop de retry infinito.

A fila `order-created` já aponta pra dead letter exchange; falta declarar a fila morta e o binding dela (na `RabbitMqTopologyConfiguration`):

```java
@Bean
Queue orderCreatedDeadLetterQueue() {
    return new Queue("order-created.dlq");
}

@Bean
Binding deadLetterBinding(Queue orderCreatedDeadLetterQueue, TopicExchange orderDeadLetterExchange) {
    return BindingBuilder.bind(orderCreatedDeadLetterQueue)
            .to(orderDeadLetterExchange)
            .with("order.created.dead");
}
```

Mensagem rejeitada (nack sem requeue) ou expirada cai na DLQ.

**Pra testar a DLQ:** quebre o consumidor de propósito — no `OrderPlacedAmqpListener`, troque o corpo do `on` por `throw new RuntimeException("falha proposital")`, publique de novo e confira no console `http://localhost:15672`: a fila `order-created.dlq` recebe a mensagem. Depois reverta.

O resumo dos três canais: JMS é fila clássica ponto a ponto, simples e padrão corporativo; RabbitMQ é fila com roteamento flexível por exchanges e DLQ integrada; Kafka é log distribuído pra volume e reprocessamento de histórico.

## Módulo 5 — Spring Modulith

**A ideia:** o Modulith estrutura o monólito em **módulos** com limites claros, e dá **entrega garantida** de eventos entre eles. A peça central é o Event Publication Registry: cada evento publicado vira uma linha numa tabela. Se o listener falha, a linha fica pendente e o evento é **republicado no restart**. Assim a integração entre módulos não perde mensagem na queda do consumidor.

Dependências da seção:

```kotlin
implementation("org.springframework.modulith:spring-modulith-starter-core")
implementation("org.springframework.modulith:spring-modulith-starter-jpa")
```

O `core` traz o `@Modulithic` e o `@ApplicationModuleListener`; o `jpa` habilita o registro em tabela.

A aplicação ganha `@Modulithic`:

```java
package com.example.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
public class ShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}
```

O listener de integração. O `@ApplicationModuleListener` é um atalho pra `@Transactional` + `@TransactionalEventListener` + `@Async`: reage ao mesmo `OrderPlaced` do módulo 1, mas depois do commit, em transação e thread próprias:

```java
package com.example.shop.order;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedIntegrationListener {

    @ApplicationModuleListener
    public void on(OrderPlaced event) {
        // integração entre módulos, em transação própria
    }
}
```

Configuração — cria a tabela do registro com o H2 e re-publica pendências no restart:

```yaml
spring:
  modulith:
    republish-outstanding-events-on-restart: true
    events:
      jpa:
        schema-initialization:
          enabled: true
```

**Pra testar este módulo:** nada externo — o H2 guarda o registro. Rode o app e use o `POST /api/orders` do módulo 1: além do log in-process, o evento vira linha na tabela `event_publication` (dá pra conferir no console do H2 ou num select no log). Pra ver a republicação: comente o `@ApplicationModuleListener`, crie um pedido, reinicie o app e descomente — o evento pendente é republicado e o listener roda.

O Modulith também verifica os limites entre módulos em teste (`ApplicationModules.of(...).verify()`), garantindo que nenhum módulo fuça o interno do outro — isso entra na aula de testes.

## Spring Integration (menção)

Spring Integration implementa Enterprise Integration Patterns: canais, transformers, filtros, roteadores, adapters. Você monta um fluxo de mensagens como um pipeline declarativo de componentes. O overlap com esta aula é grande: o que o Integration faz com EIP, o Modulith faz com eventos e listener dentro do processo, e o Kafka/JMS fazem entre processos.

Hoje o Integration perde espaço pro Spring Cloud Stream quando o fluxo é distribuído, e pro Modulith quando é interno. Conhecer vale pra código legado e pra fluxo de ETL; projeto novo usa o que já está aqui.

## Estrutura

```
src/main/java/com/example/shop/
├── ShopApplication.java
├── order/
│   ├── Order.java
│   ├── OrderRepository.java
│   ├── CreateOrderRequest.java
│   ├── OrderPlaced.java
│   ├── OrderService.java
│   ├── OrderController.java
│   ├── OrderCreatedListener.java
│   ├── InvoiceRequestService.java
│   ├── InvoiceRequestListener.java
│   ├── InvoiceController.java
│   ├── OrderPlacedKafkaPublisher.java
│   ├── OrderPlacedKafkaListener.java
│   ├── OrderKafkaController.java
│   ├── OrderPlacedAmqpPublisher.java
│   ├── OrderPlacedAmqpListener.java
│   ├── OrderAmqpController.java
│   └── OrderCreatedIntegrationListener.java
└── config/
    ├── JmsConfiguration.java
    └── RabbitMqTopologyConfiguration.java
src/main/resources/
├── application.yaml
└── requests.http
docker-compose.yml
```