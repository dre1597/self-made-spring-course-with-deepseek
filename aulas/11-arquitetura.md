# Aula 11 — Arquitetura

Objetivo: escolher entre monólito modular e microserviços, usar Spring Cloud e decidir entre MVC e WebFlux.

## Monólito modular vs microserviços

Comece com monólito modular. O Spring Modulith organiza o código em módulos com limites verificados, e o Event Publication Registry resolve a integração entre eles. Você deploya uma unidade, sem orquestrar rede.

Vá pra microserviços quando um desses ficar verdadeiro:

- Times diferentes precisam deployar em ritmos diferentes.
- Uma parte do sistema escala muito mais que o resto.
- Um serviço precisa de stack ou isolamento próprio.

O custo de microserviços é alto: rede, consistência eventual, observabilidade distribuída, deploy orquestrado. Monólito modular entrega os mesmos limites lógicos sem pagar isso antecipado. A regra: módulos primeiro; extraia serviço quando o deploy independente virar necessidade, não desejo.

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

Centraliza a config de todos os serviços num lugar, versionada em git. O server:

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
spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/exemplo/config-repo
```

O client puxa a config na subida:

```kotlin
implementation("org.springframework.cloud:spring-cloud-starter-config")
```

```yaml
spring:
  application:
    name: books
  config:
    import: optional:configserver:http://localhost:8888
```

Cada serviço lê `books.yaml` do repositório de config, e a mudança de propriedade vira commit, com histórico.

### Discovery

O Eureka registra as instâncias e resolve pelo nome do serviço, sem IP fixo. O server:

```java
package com.example.eurekaserver;

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

O client se registra sozinho com o starter:

```kotlin
implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
```

```yaml
spring:
  application:
    name: books
eureka:
  client:
    serviceUrl:
      defaultZone: http://localhost:8761/eureka/
```

Com o nome registrado, outro serviço chama `http://books/api/...` e o load balance resolve a instância. Discovery e config andam juntos: o config server também se registra, e os clientes o acham pelo nome.

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
spring:
  cloud:
    gateway:
      routes:
        - id: books
          uri: lb://books
          predicates:
            - Path=/api/books/**
        - id: loans
          uri: lb://loans
          predicates:
            - Path=/api/loans/**
```

`lb://books` resolve o serviço pelo discovery e balanceia entre as instâncias. O cliente externo só conhece o gateway; os serviços internos ficam escondidos. Filtros entram por rota: rewrite de path, header extra, rate limit por usuário. A dependência é `spring-cloud-starter-gateway`. O gateway em si é WebFlux mesmo que os serviços atrás sejam MVC; ele só encaminha.

### Circuit Breaker

Retry (aula 06) resolve falha transitória; circuit breaker resolve dependência que fica fora por um período. Quando a taxa de falha passa do limite, o circuito abre e falha rápido sem chamar o serviço, poupando ele de carga. Depois de um tempo deixa passar uma requisição de teste e reabre se funcionar.

O Resilience4j é a implementação padrão do Spring Cloud:

```kotlin
implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
```

```java
@CircuitBreaker(name = "priceService", fallbackMethod = "priceFallback")
public Price findPrice(Long bookId) {
    return priceClient.findPrice(bookId);
}

public Price priceFallback(Long bookId, Throwable cause) {
    return Price.unavailable(bookId);
}
```

```yaml
resilience4j:
  circuitbreaker:
    instances:
      priceService:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
```

`failureRateThreshold: 50` abre o circuito quando metade das últimas 10 chamadas falha. `fallbackMethod` devolve uma resposta de contingência (preço padrão, cache) em vez de explodir. A diferença pro `@Retryable`: retry insiste na mesma chamada; circuit breaker para de chamar e entrega a contingência.

## MVC + virtual threads vs WebFlux

Em 2026 a resposta padrão é MVC com virtual threads. A virtual thread bloqueia sem custo, então o argumento clássico do WebFlux (não bloquear thread de pool) perdeu força. Você mantém o modelo síncrono, com JPA e debug triviais, e ainda escala bem.

WebFlux só quando precisa de:

- Backpressure: fluxo contínuo onde o produtor tem que respeitar o ritmo do consumidor.
- Streaming reativo de ponta a ponta, sem pular pra threads.

WebFlux custa caro: todo o stack é reativo (repositórios, drivers, clientes), e erro de `block()` escondido vira gargalo. Pra API CRUD com banco e chamadas HTTP, MVC + virtual threads resolve.

Resumo: monólito modular primeiro, Spring Cloud pra config e discovery quando distribuir, e MVC + virtual threads salvo exceção reativa.

## Estrutura

```
config-server/
├── build.gradle.kts
└── src/main/java/com/example/configserver/
    └── ConfigServerApplication.java
eureka-server/
├── build.gradle.kts
└── src/main/java/com/example/eurekaserver/
    └── EurekaServerApplication.java
gateway/
├── build.gradle.kts
└── src/main/java/com/example/gateway/
    └── GatewayApplication.java
```
