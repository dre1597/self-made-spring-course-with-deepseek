# Aula 11 — Arquitetura

Objetivo: organizar um monólito modular, reconhecer quando extrair serviços, usar Spring Cloud para distribuir a aplicação e escolher entre MVC e WebFlux.

Domínio: uma plataforma de cursos online com catálogo de cursos, matrículas e recomendações. O projeto é próprio desta aula e não depende dos projetos das aulas anteriores.

## Base do projeto

O monólito modular começa com uma única aplicação e dois módulos de negócio. A API permite observar o limite entre eles sem introduzir rede antes da hora.

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
runtimeOnly("com.h2database:h2")
```

O `spring-boot-starter` fornece o contexto e as transações. O `webmvc` expõe a API do catálogo e das matrículas. O `data-jpa` com H2 permite executar o projeto sem infraestrutura externa.

A aplicação principal:

```java
package com.example.learning;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LearningApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningApplication.class, args);
    }
}
```

## Monólito modular vs microserviços

Comece com monólito modular quando os módulos pertencem ao mesmo produto e ainda podem compartilhar um processo. O Spring Modulith verifica os limites dos pacotes e registra eventos entre módulos. Você deploya uma unidade, sem transformar cada chamada em uma operação de rede.

Adicione o Modulith ao projeto:

```kotlin
implementation(platform("org.springframework.modulith:spring-modulith-bom:2.1.1"))
implementation("org.springframework.modulith:spring-modulith-starter-core")
testImplementation("org.springframework.modulith:spring-modulith-starter-test")
```

Marque a classe principal mostrada acima com `@Modulithic`. A anotação mantém o comportamento do Boot e acrescenta a verificação dos módulos.

O pacote raiz vira um módulo, e cada subpacote direto vira outro. O catálogo publica um evento sem conhecer o módulo de matrículas:

```java
package com.example.learning.catalog;

public record CoursePublished(Long courseId, String title) {
}
```

```java
package com.example.learning.catalog;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {

    private final ApplicationEventPublisher eventPublisher;

    public CatalogService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public CoursePublished publish(Long courseId, String title) {
        CoursePublished event = new CoursePublished(courseId, title);
        eventPublisher.publishEvent(event);
        return event;
    }
}
```

O catálogo oferece um endpoint simples para testar o módulo diretamente e atrás do gateway:

```java
package com.example.learning.catalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseCatalogController {

    private final CatalogService catalogService;

    public CourseCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<CourseSummary> list() {
        return List.of(new CourseSummary(1L, "Arquitetura de sistemas"));
    }

    @PostMapping
    public CoursePublished publish(@RequestBody PublishCourseRequest request) {
        return catalogService.publish(request.courseId(), request.title());
    }

    public record PublishCourseRequest(Long courseId, String title) {
    }

    public record CourseSummary(Long id, String title) {
    }
}
```

Teste sem gateway:

```http
### Catálogo local
GET http://localhost:8081/api/courses

### Publica curso e dispara evento modular
POST http://localhost:8081/api/courses
Content-Type: application/json

{
  "courseId": 1,
  "title": "Arquitetura de sistemas"
}
```

O módulo de matrículas depende do evento, não da classe interna do catálogo:

```java
package com.example.learning.enrollment;

import com.example.learning.catalog.CoursePublished;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class CoursePublicationListener {

    private static final Logger logger = LoggerFactory.getLogger(CoursePublicationListener.class);

    @ApplicationModuleListener
    public void on(CoursePublished event) {
        logger.info("Curso {} disponível para matrícula", event.courseId());
    }
}
```

Verifique os limites como um teste comum:

```java
package com.example.learning;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

@SuppressWarnings("unused")
class ArchitectureTest {

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(LearningApplication.class).verify();
    }
}
```

Vá pra microserviços quando um desses ficar verdadeiro:

- Times diferentes precisam deployar em ritmos diferentes.
- Uma parte do sistema escala muito mais que o resto.
- Um serviço precisa de stack ou isolamento próprio.

Microserviços adicionam rede, consistência eventual, observabilidade distribuída e deploy orquestrado. O monólito modular entrega limites lógicos sem pagar esse custo antes da necessidade. Extraia um módulo quando o deploy independente, a escala ou o isolamento justificarem a operação.

## Spring Cloud

O Spring Cloud cobre os padrões de sistema distribuído. A versão compatível com Boot 4.1 é a `2025.1.x` (Oakwood). Gerencie via BOM:

```kotlin
dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.3")
    }
}
```

### Config

O Config Server centraliza propriedades fora do código dos serviços. Para a primeira execução, use o backend `native`, que lê uma pasta local. Depois, troque essa pasta por um repositório Git privado sem mudar o cliente.

```kotlin
implementation("org.springframework.cloud:spring-cloud-config-server")
```

```java
package com.example.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

```yaml
# application.yaml do Config Server
server:
  port: 8888
spring:
  cloud:
    config:
      server:
        native:
          search-locations: file:${CONFIG_REPOSITORY:./config-repository}
  profiles:
    active: native
```

Prepare a pasta local e crie `config-repository/learning-catalog.yaml` com a porta que o serviço vai consumir:

```bash
mkdir -p config-repository
```

```yaml
server:
  port: 8081
```

O client puxa essa configuração na subida:

```kotlin
implementation("org.springframework.cloud:spring-cloud-starter-config")
```

```yaml
spring:
  application:
    name: learning-catalog
  cloud:
    config:
      import: configserver:http://localhost:8888
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

O client procura `learning-catalog.yaml` no Config Server. Consulte `http://localhost:8888/learning-catalog/default` para ver o documento que o server entrega. Com o import sem `optional`, o serviço falha na subida se o Config Server estiver indisponível, em vez de iniciar com configuração desconhecida. Em produção, substitua o backend `native` por Git e proteja o endpoint do Config Server.

### Discovery

O Eureka registra as instâncias e resolve pelo nome do serviço, sem IP fixo. O server:

```kotlin
implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-server")
```

```java
package com.example.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

Configuração do server em `application.yaml`:

```yaml
server:
  port: 8761
eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
```

O client se registra sozinho com o starter:

```kotlin
implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
```

```yaml
spring:
  application:
    name: learning-catalog
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

Suba o Eureka na porta `8761`, depois suba duas instâncias do catálogo com o mesmo `spring.application.name`. Consulte `http://localhost:8761/eureka/apps` para observar as instâncias registradas. Discovery resolve o nome, mas não transforma uma chamada HTTP comum em chamada balanceada; o cliente precisa do Spring Cloud LoadBalancer.

### Gateway

O gateway é a porta única de entrada: recebe a requisição externa, roteia pro serviço interno pelo nome do discovery e aplica filtros (auth, rate limit, rewrite). O Spring Cloud Gateway roda em cima do WebFlux:

```java
package com.example.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
```

Roteamento por config:

```yaml
server:
  port: 8080
spring:
  application:
    name: learning-gateway
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: catalog
              uri: lb://learning-catalog
              predicates:
                - Path=/api/courses/**
            - id: enrollment
              uri: lb://learning-enrollment
              predicates:
                - Path=/api/enrollments/**
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

`lb://learning-catalog` resolve o serviço pelo discovery e balanceia entre as instâncias. O cliente externo só conhece o gateway; os serviços internos ficam escondidos. Para observar o encaminhamento, rode `curl http://localhost:8080/api/courses` e acompanhe os logs do gateway e do catálogo. Filtros entram por rota: rewrite de path, header extra, rate limit por usuário. As dependências:

```kotlin
implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
```

O gateway roda em cima do WebFlux (o starter traz o `webflux`), mesmo que os serviços atrás sejam MVC; ele só encaminha.

### Circuit breaker

Retry resolve uma falha transitória; circuit breaker protege o serviço quando uma dependência permanece indisponível. Depois de acumular falhas, o circuito abre, para de chamar o catálogo e entrega uma resposta de contingência. Depois de um intervalo, permite uma chamada de teste.

No serviço de matrículas, adicione o cliente HTTP, o load balancer, o Actuator e o Resilience4j:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-restclient")
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("org.springframework.boot:spring-boot-starter-aspectj")
implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
implementation("io.github.resilience4j:resilience4j-spring-boot4")
implementation("io.github.resilience4j:resilience4j-micrometer")
```

O `@LoadBalanced` permite usar o nome registrado no Eureka como host:

```java
package com.example.learning.enrollment;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class EnrollmentClientConfiguration {

    @Bean
    @LoadBalanced
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
```

O serviço chama o catálogo e devolve uma recomendação de contingência quando o circuito abre:

```java
package com.example.learning.enrollment;

import java.util.List;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CourseRecommendationService {

    private final RestClient restClient;

    public CourseRecommendationService(RestClient.Builder builder) {
        this.restClient = builder.baseUrl("http://learning-catalog").build();
    }

    @CircuitBreaker(name = "catalog", fallbackMethod = "catalogFallback")
    public List<CourseSummary> findFeaturedCourses() {
        return restClient.get()
                .uri("/api/courses")
                .retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() {
                });
    }

    List<CourseSummary> catalogFallback(Throwable cause) {
        return List.of(new CourseSummary(0L, "Catálogo indisponível"));
    }

    public record CourseSummary(Long id, String title) {
    }
}
```

Exponha o comportamento para conseguir dispará-lo:

```java
package com.example.learning.enrollment;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final CourseRecommendationService recommendationService;

    public RecommendationController(CourseRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping
    public List<CourseRecommendationService.CourseSummary> list() {
        return recommendationService.findFeaturedCourses();
    }
}
```

```yaml
resilience4j:
  circuitbreaker:
    instances:
      catalog:
        slidingWindowSize: 10
        minimumNumberOfCalls: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
```

```yaml
# application.yaml do learning-enrollment
server:
  port: 8082
spring:
  application:
    name: learning-enrollment
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

`failureRateThreshold: 50` abre o circuito quando metade das últimas dez chamadas falha. Pare o catálogo, rode `curl http://localhost:8082/api/recommendations` dez vezes e observe a resposta de fallback e os logs. O endpoint `/actuator/metrics/resilience4j.circuitbreaker.calls` mostra as chamadas do circuito quando o Actuator estiver exposto. Religue o catálogo e aguarde os 30 segundos para a chamada de teste.

## MVC + virtual threads vs WebFlux

Para o catálogo e as matrículas, use MVC com virtual threads. O modelo síncrono combina com JPA e clientes bloqueantes, e a virtual thread reduz o custo de esperar I/O sem exigir que o domínio inteiro vire reativo:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

Uma virtual thread ainda consome memória e ainda pode segurar conexão de banco ou socket. Ela não transforma uma chamada bloqueante em não bloqueante; apenas evita ocupar uma thread de plataforma durante parte da espera.

Escolha WebFlux quando o fluxo reativo atravessar a aplicação inteira, como um serviço de streaming de progresso. Esse serviço é separado do catálogo:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-webflux")
```

Configure esse processo na porta `8083`:

```yaml
server:
  port: 8083
```

```java
package com.example.learning.stream;

import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class ProgressStreamController {

    @GetMapping(value = "/api/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Progress> stream() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(step -> new Progress(step, "processando"));
    }

    public record Progress(long step, String status) {
    }
}
```

Observe o streaming sem o cliente esperar o corpo inteiro:

```bash
curl -N http://localhost:8083/api/progress
```

WebFlux exige repositórios, clientes e operadores reativos de ponta a ponta. `block()` escondido devolve o gargalo que o modelo reativo tentava evitar. Para CRUD com JPA e chamadas HTTP bloqueantes, MVC com virtual threads mantém o código menor e o diagnóstico mais direto.

Ao extrair o catálogo, mova suas classes para `learning-catalog` e mantenha o mesmo endpoint. O gateway e o serviço de matrículas passam a depender do nome registrado, não do pacote do monólito.

## Estrutura

```
learning-monolith/
├── src/main/java/com/example/learning/
│   ├── LearningApplication.java
│   ├── catalog/
│   │   ├── CatalogService.java
│   │   ├── CourseCatalogController.java
│   │   └── CoursePublished.java
│   └── enrollment/
│       └── CoursePublicationListener.java
└── src/test/java/com/example/learning/
    └── ArchitectureTest.java
config-server/
├── src/main/java/com/example/configserver/
│   └── ConfigServerApplication.java
config-repository/
└── learning-catalog.yaml
eureka-server/
├── src/main/java/com/example/discovery/
│   └── EurekaServerApplication.java
learning-catalog/
├── src/main/java/com/example/learning/catalog/
│   ├── CatalogApplication.java
│   ├── CatalogService.java
│   ├── CourseCatalogController.java
│   └── CoursePublished.java
learning-enrollment/
├── src/main/java/com/example/learning/enrollment/
│   ├── EnrollmentApplication.java
│   ├── CourseRecommendationService.java
│   ├── EnrollmentClientConfiguration.java
│   └── RecommendationController.java
learning-gateway/
├── src/main/java/com/example/gateway/
│   └── GatewayApplication.java
learning-stream/
└── src/main/java/com/example/learning/stream/
    ├── ProgressStreamController.java
    └── StreamApplication.java
```
