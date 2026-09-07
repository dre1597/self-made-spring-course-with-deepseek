# Aula 07 — Eventos e messaging

Objetivo: publicar eventos de domínio, mandar pra Kafka, RabbitMQ e JMS, e fechar a integração entre módulos com Spring Modulith.

## Dependências

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-artemis")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
    runtimeOnly("com.h2database:h2")
}
```

## Eventos de domínio

O `ApplicationEventPublisher` publica eventos no mesmo processo. O evento:

```java
package com.example.books.book;

public record BookCreated(Long bookId, String title) {
}
```

O serviço publica dentro da transação:

```java
package com.example.books.book;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookService {

    private final BookRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public BookService(BookRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Book create(CreateBookRequest request) {
        Book book = repository.save(new Book(request.title(), request.author()));
        eventPublisher.publishEvent(new BookCreated(book.getId(), book.title()));
        return book;
    }
}
```

O listener reage pelo tipo do parâmetro:

```java
package com.example.books.book;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BookCreatedListener {

    @EventListener
    public void on(BookCreated event) {
        // indexação, cache, log...
    }
}
```

`@EventListener` roda na mesma transação do publisher. Pra rodar só depois do commit, troque por `@TransactionalEventListener`:

```java
@TransactionalEventListener
public void on(BookCreated event) {
    // aqui a transação já foi commitada
}
```

## Kafka

Pra eventos entre processos. O Boot auto-configura o `KafkaTemplate` e o listener container.

Publicação:

```java
package com.example.books.book;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class BookEventPublisher {

    private final KafkaTemplate<String, BookCreated> kafkaTemplate;

    public BookEventPublisher(KafkaTemplate<String, BookCreated> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(BookCreated event) {
        kafkaTemplate.send("book-created", event);
    }
}
```

Consumo:

```java
package com.example.books.book;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookCreatedKafkaListener {

    @KafkaListener(topics = "book-created")
    public void on(BookCreated event) {
        // processa o evento vindo de outro serviço
    }
}
```

Apontando o broker:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
```

## JmsClient

O Spring 7 trocou o `JmsTemplate` pelo `JmsClient`, uma API fluente no mesmo espírito do `RestClient`. O Boot auto-configura o `JmsClient` com o `spring-boot-starter-artemis`.

Envio:

```java
package com.example.books.book;

import org.springframework.jms.core.JmsClient;
import org.springframework.stereotype.Service;

@Service
public class BookNotificationService {

    private final JmsClient jmsClient;

    public BookNotificationService(JmsClient jmsClient) {
        this.jmsClient = jmsClient;
    }

    public void notify(BookCreated event) {
        jmsClient.destination("book-notifications")
                .send(event);
    }
}
```

Recebimento síncrono, com timeout:

```java
Optional<BookCreated> received = jmsClient.destination("book-notifications")
        .withReceiveTimeout(1000)
        .receive(BookCreated.class);
```

Consumo por listener:

```java
package com.example.books.book;

import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class BookNotificationListener {

    @JmsListener(destination = "book-notifications")
    public void on(BookCreated event) {
        // processa a notificação
    }
}
```

Configuração do broker:

```yaml
spring:
  artemis:
    mode: embedded
```

Kafka é log distribuído, bom pra streams e replay; JMS é fila clássica, bom pra trabalho e notificação ponto a ponto.

## RabbitMQ

AMQP é o protocolo do RabbitMQ. O modelo é diferente de Kafka e JMS: a mensagem vai pra uma **exchange**, que roteia pra filas por **routing key** e **binding**. O `spring-boot-starter-amqp` traz o `RabbitTemplate` e os listeners.

Publicação:

```java
package com.example.books.book;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class BookEventAmqpPublisher {

    private final RabbitTemplate rabbitTemplate;

    public BookEventAmqpPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(BookCreated event) {
        rabbitTemplate.convertAndSend("book-exchange", "book.created", event);
    }
}
```

`convertAndSend` serializa o evento (Jackson) e manda pra exchange com a routing key `book.created`. O consumo:

```java
package com.example.books.book;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BookCreatedAmqpListener {

    @RabbitListener(queues = "book-created")
    public void on(BookCreated event) {
        // processa o evento vindo de outro serviço
    }
}
```

A topologia (exchange, fila e binding) você declara como beans:

```java
package com.example.books.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqTopologyConfiguration {

    @Bean
    TopicExchange bookExchange() {
        return new TopicExchange("book-exchange");
    }

    @Bean
    Queue bookCreatedQueue() {
        return new Queue("book-created");
    }

    @Bean
    Binding bookCreatedBinding(Queue bookCreatedQueue, TopicExchange bookExchange) {
        return BindingBuilder.bind(bookCreatedQueue).to(bookExchange).with("book.created");
    }
}
```

O broker:

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
```

### Dead Letter Queue

Quando o consumidor falha e o retry esgota, a mensagem não pode sumir. A DLQ recebe o que não deu. Você declara a fila apontando a dead letter exchange:

```java
@Bean
Queue bookCreatedQueue() {
    return QueueBuilder.durable("book-created")
            .deadLetterExchange("book-dlx")
            .deadLetterRoutingKey("book.created.dead")
            .build();
}

@Bean
Queue bookCreatedDeadLetterQueue() {
    return new Queue("book-created.dlq");
}

@Bean
Binding deadLetterBinding(Queue bookCreatedDeadLetterQueue, TopicExchange deadLetterExchange) {
    return BindingBuilder.bind(bookCreatedDeadLetterQueue)
            .to(deadLetterExchange)
            .with("book.created.dead");
}
```

Mensagem rejeitada (nack sem requeue) ou expirada cai na DLQ. Ali você inspeciona o que travou, reprocessa com correção ou registra e descarta. Sem DLQ, a falha vira perda silenciosa ou loop de retry infinito.

O resumo dos três: Kafka é log distribuído pra streams e replay; RabbitMQ é fila com roteamento flexível por exchanges, com DLQ integrada; JMS é fila clássica ponto a ponto, simples e padrão corporativo. RabbitMQ brilha em roteamento e fila de trabalho com entrega garantida; Kafka, em volume e reprocessamento de histórico.

## Spring Modulith

O Modulith estrutura o monólito em módulos e dá garantias de entrega entre eles. O `@ApplicationModuleListener` é atalho pra `@Transactional` + `@TransactionalEventListener` + `@Async`:

```java
package com.example.books.book;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class BookCreatedIntegrationListener {

    @ApplicationModuleListener
    public void on(BookCreated event) {
        // integração entre módulos, em transação própria
    }
}
```

O ganho vem do Event Publication Registry: cada evento publicado vira uma linha numa tabela. Se o listener falha, a linha fica pendente e é republicada no restart.

Aplicação com `@Modulithic`:

```java
package com.example.books;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
public class BooksApplication {

    public static void main(String[] args) {
        SpringApplication.run(BooksApplication.class, args);
    }
}
```

Configuração:

```yaml
spring:
  modulith:
    republish-outstanding-events-on-restart: true
    events:
      jpa:
        schema-initialization:
          enabled: true
```

O Modulith também verifica os limites entre módulos em teste (`ApplicationModules.of(...).verify()`), garantindo que nenhum módulo fuça o interno do outro.

## Spring Integration (menção)

Spring Integration implementa Enterprise Integration Patterns: canais, transformers, filtros, roteadores, adapters. Você monta um fluxo de mensagens como um pipeline declarativo de componentes. O overlap com esta aula é grande: o que o Integration faz com EIP, o Modulith faz com eventos e listener dentro do processo, e o Kafka/JMS fazem entre processos.

Hoje o Integration perde espaço pro Spring Cloud Stream quando o fluxo é distribuído, e pro Modulith quando é interno. Conhecer vale pra código legado e pra fluxo de ETL; projeto novo usa o que já está aqui.

## Estrutura

```
src/main/java/com/example/books/
├── BooksApplication.java
├── book/
│   ├── Book.java
│   ├── BookRepository.java
│   ├── BookService.java
│   ├── BookCreated.java
│   ├── BookCreatedListener.java
│   ├── BookCreatedIntegrationListener.java
│   ├── BookEventPublisher.java
│   ├── BookCreatedKafkaListener.java
│   ├── BookEventAmqpPublisher.java
│   ├── BookCreatedAmqpListener.java
│   ├── BookNotificationService.java
│   └── BookNotificationListener.java
└── config/
    └── RabbitMqTopologyConfiguration.java
src/main/resources/
└── application.yaml
```
