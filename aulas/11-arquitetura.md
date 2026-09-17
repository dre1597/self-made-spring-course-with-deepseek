# Aula 11 — Arquitetura

Objetivo: organizar um monólito modular, reconhecer quando extrair serviços, usar Spring Cloud para distribuir uma aplicação e escolher entre MVC e WebFlux.

Domínio: uma plataforma de cursos online com catálogo de cursos, matrículas e recomendações. A aula usa projetos próprios e independentes. O primeiro é o `learning-monolith`; os exemplos distribuídos seguintes são projetos novos, cada um com sua própria porta e seu próprio ciclo de execução.

## A pergunta arquitetural

Arquitetura não começa escolhendo uma tecnologia. Começa decidindo onde cada responsabilidade mora, quem pode depender de quem e qual unidade será publicada em produção.

Nesta aula, a plataforma tem duas áreas: `catalog`, que é dona dos cursos, e `enrollment`, que usa a informação de um curso publicado para continuar o fluxo de matrícula. A primeira decisão é organizar essas áreas dentro de um único processo. A segunda é comparar o que muda quando elas passam a ser processos independentes.

O resultado não é “monólito agora, microserviço depois” como uma obrigação. São duas topologias possíveis para o mesmo domínio:

- **monólito modular**: módulos separados, um processo e um deploy;
- **serviços distribuídos**: processos separados, comunicação pela rede e deploy independente.

O Spring Modulith ajuda a tornar os limites do primeiro modelo verificáveis. O Spring Cloud mostra parte da infraestrutura necessária para o segundo. MVC e WebFlux entram como decisões internas de cada aplicação, não como sinônimos de monólito ou microserviço.

## Base do projeto

O monólito modular começa com uma única aplicação e dois módulos de negócio. A API permite observar o limite entre eles sem introduzir rede antes da hora. Este projeto usa sempre a porta `8080`; nenhuma etapa posterior altera a configuração dele.

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
implementation(platform("org.springframework.modulith:spring-modulith-bom:2.1.1"))
implementation("org.springframework.modulith:spring-modulith-starter-jpa")
runtimeOnly("com.h2database:h2")
testImplementation("org.springframework.modulith:spring-modulith-starter-test")
```

O `spring-boot-starter` fornece o contexto da aplicação. O `webmvc` expõe a API do catálogo e das matrículas. O `data-jpa` integra o domínio ao banco. O H2 roda esse banco localmente sem infraestrutura externa.

O BOM do Modulith mantém as versões dos módulos alinhadas. O `spring-modulith-starter-jpa` adiciona a verificação dos módulos e o suporte de eventos persistidos com JPA; por isso ele é usado junto do `spring-boot-starter-data-jpa`. O `spring-modulith-starter-test` fornece o suporte para testar os limites arquiteturais.

A aplicação principal:

```java
package com.example.learningmonolith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
public class LearningMonolithApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningMonolithApplication.class, args);
    }
}
```

O `learning-monolith` usa a porta `8080` desde o começo. Mantenha este arquivo em `src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: learning-monolith
server:
  port: 8080
```

## Monólito modular na prática

Um monólito modular é uma aplicação única dividida em módulos que têm responsabilidades e APIs próprias. Os módulos compartilham processo e deploy, mas não podem depender livremente das classes uns dos outros.

Neste projeto, `catalog` é dono dos cursos. `enrollment` reage à publicação de um curso, mas não acessa o `CatalogService`, o repository ou qualquer detalhe interno do catálogo. A única dependência permitida entre eles será o evento público `CoursePublished`.

Essa organização produz três benefícios concretos: cada módulo tem uma responsabilidade compreensível, mudanças ficam localizadas e a dependência entre módulos aparece no código em vez de ficar escondida em chamadas arbitrárias. O processo continua único, então não há timeout, DNS ou consistência eventual entre `catalog` e `enrollment`.

O teste arquitetural é apenas um guardrail dessa decisão. Ele não define o que é arquitetura nem transforma o monólito em uma etapa de migração; ele avisa quando uma mudança de código viola os limites que a arquitetura já estabeleceu.

`@Modulithic` mantém o comportamento do Boot e acrescenta a verificação dos módulos. O pacote raiz vira um módulo, e cada subpacote direto vira outro.

O catálogo publica um evento sem conhecer o módulo de matrículas. O evento fica em um subpacote público próprio:

```java
package com.example.learningmonolith.catalog.events;

public record CoursePublished(Long courseId, String title) {
}
```

Marque esse subpacote como uma API nomeada do módulo `catalog` em `catalog/events/package-info.java`:

```java
@org.springframework.modulith.NamedInterface("events")
package com.example.learningmonolith.catalog.events;
```

Agora declare que `enrollment` só pode depender dessa API em `enrollment/package-info.java`:

```java
@org.springframework.modulith.ApplicationModule(allowedDependencies = "catalog :: events")
package com.example.learningmonolith.enrollment;
```

A string `catalog :: events` significa “a interface nomeada `events` do módulo `catalog`”. Se `enrollment` importar `CatalogService`, `CourseRepository` ou qualquer outro detalhe do catálogo, a verificação arquitetural falha.

```java
package com.example.learningmonolith.catalog;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.learningmonolith.catalog.events.CoursePublished;

@Service
public class CatalogService {

    private final ApplicationEventPublisher eventPublisher;

    public CatalogService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CoursePublished publish(Long courseId, String title) {
        CoursePublished event = new CoursePublished(courseId, title);
        eventPublisher.publishEvent(event);
        return event;
    }
}
```

`@ApplicationModuleListener` observa o evento depois que a transação termina com sucesso. Por isso a publicação acontece dentro de um método `@Transactional`: o teste consegue provar a sequência completa, e uma transação que falhar não dispara a reação do módulo de matrículas.

O catálogo oferece um endpoint simples para testar o monólito diretamente:

```java
package com.example.learningmonolith.catalog;

import java.util.List;

import com.example.learningmonolith.catalog.events.CoursePublished;
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

Teste o `learning-monolith` diretamente, sem gateway:

```http
### Catálogo local
GET http://localhost:8080/api/courses

### Publica curso e dispara evento modular
POST http://localhost:8080/api/courses
Content-Type: application/json

{
  "courseId": 1,
  "title": "Arquitetura de sistemas"
}
```

O módulo de matrículas depende do evento, não da classe interna do catálogo:

```java
package com.example.learningmonolith.enrollment;

import com.example.learningmonolith.catalog.events.CoursePublished;
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

Agora o teste tem uma regra concreta para verificar: `enrollment` só pode conhecer `catalog :: events`. Ele também detecta ciclos entre módulos. Não é um teste de endpoint nem de regra de negócio; é uma inspeção estrutural do código executada pelo JUnit.

Verifique os limites com este teste arquitetural:

```java
package com.example.learningmonolith;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

@SuppressWarnings("unused")
class ArchitectureTest {

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(LearningMonolithApplication.class).verify();
    }
}
```

O teste passa porque o listener importa apenas `catalog.events.CoursePublished`. Para confirmar o propósito da regra, imagine que alguém injete `CatalogService` no listener ou importe um repository do catálogo. Ao rodar `./gradlew test`, `ApplicationModules.verify()` identifica a dependência fora da interface nomeada e falha antes da aplicação ser publicada.

Neste ponto, não há Config Server, Eureka, gateway ou segunda aplicação. Execute os comandos dentro do diretório do projeto `learning-monolith`; o segundo comando mantém a aplicação rodando:

```bash
./gradlew test
./gradlew bootRun
```

Com a aplicação em `8080`, envie o request do arquivo `requests.http`. O endpoint retorna o curso publicado e o log registra a reação do módulo de matrículas. O teste arquitetural verifica a fronteira dos pacotes; o request verifica o fluxo de evento dentro do mesmo processo.

## Quando mudar para microserviços

O monólito modular não é uma etapa provisória que precisa ser desmontada por obrigação. Ele é uma forma de manter módulos separados enquanto eles ainda podem compartilhar processo, banco e deploy. A rede só deve entrar quando existir uma necessidade concreta de separar o ciclo de vida de uma parte do produto.

Considere uma extração quando pelo menos uma destas condições for real:

- Times diferentes precisam deployar em ritmos diferentes.
- Uma parte do sistema escala muito mais que o resto.
- Um serviço precisa de stack ou isolamento próprio.

Microserviços adicionam rede, consistência eventual, observabilidade distribuída e deploy orquestrado. Uma chamada que antes era um método passa a depender de DNS, timeout, retry, autenticação e contrato HTTP. O ganho é o deploy independente, a escala isolada ou o isolamento técnico; se nenhum deles for necessário, o custo não se paga.

Nesta aula, a extração é didática: o catálogo e o enrollment serão recriados como projetos independentes para tornar visível a fronteira. O `learning-monolith` não será convertido nem terá sua porta alterada.

Na parte seguinte, a comparação usa projetos novos. O `learning-monolith` continua existindo na porta `8080` e não será alterado para virar um serviço. Os projetos distribuídos usam estas portas fixas:

| Projeto | Responsabilidade | Porta |
| --- | --- | ---: |
| `config-server` | Entregar configuração externa | `8888` |
| `eureka-server` | Registrar e descobrir serviços | `8761` |
| `learning-catalog` | Expor o catálogo extraído | `8081` |
| `learning-enrollment` | Consultar o catálogo e recomendar cursos | `8082` |
| `learning-stream` | Demonstrar streaming com WebFlux | `8083` |
| `learning-gateway` | Ser a entrada HTTP dos serviços | `8084` |

Cada projeto tem seu próprio `main`, `build.gradle.kts` e configuração. Quando um exemplo disser para iniciar outro processo, trata-se de outro projeto, não de uma nova configuração do monólito.

Para executar um deles, entre no diretório correspondente e rode `./gradlew bootRun`. Não use o `build.gradle.kts` de um projeto em outro: cada processo tem suas próprias dependências.

O `CoursePublished` do monólito é um evento em memória, entregue pelo contexto daquela aplicação. Ele não atravessa a fronteira de um processo. Depois da extração, a comunicação entre `learning-catalog` e `learning-enrollment` acontece por HTTP; em um sistema real também poderia usar um broker, mas isso não será escondido neste exemplo.

## Projetos distribuídos com Spring Cloud

Quando a aplicação deixa de ser um único processo, surgem quatro perguntas práticas:

- onde cada serviço encontra sua configuração;
- como um serviço encontra outro sem conhecer seu IP;
- por qual endereço o cliente externo entra;
- o que acontece quando uma dependência está indisponível.

O Spring Cloud oferece componentes para essas perguntas. A aula apresenta um componente por vez, em projetos separados, para não esconder dependências de inicialização. A versão compatível com Boot 4.1 é a `2025.1.x` (Oakwood). Cada projeto que usa Spring Cloud importa o BOM no próprio `build.gradle.kts`; o bloco completo aparece junto das dependências de cada projeto. O `learning-stream` não precisa dele porque usa apenas Spring Boot WebFlux.

### Config

O primeiro problema é configuração. Se cada serviço guardar porta, endereço de banco e URLs de dependências dentro da própria imagem, uma mudança exige reconstruir e republicar vários projetos. O `config-server` centraliza esses valores e entrega a configuração quando o serviço inicia.

O primeiro projeto distribuído é o `config-server`, na porta `8888`. Para a primeira execução, use o backend `native`, que lê uma pasta local. Depois, essa pasta pode ser trocada por um repositório Git privado sem mudar o cliente.

```kotlin
dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.3")
    }
}

implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.cloud:spring-cloud-config-server")
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

O starter do Boot inicia a aplicação; o `webmvc` fornece o servidor HTTP; o Config Server adiciona os endpoints que entregam propriedades; e o starter de teste deixa o projeto pronto para testes JUnit. O Config Server também traz suporte web transitivo, mas a dependência explícita deixa a composição deste projeto visível.

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
# config-server/src/main/resources/application.yaml
server:
  port: 8888
spring:
  cloud:
    config:
      server:
        native:
          search-locations: file:${CONFIG_REPOSITORY:../config-repository}
  profiles:
    active: native
```

O Config Server não é o catálogo e não atende as requisições de cursos. Ele só entrega configuração. Como `config-server/` e `config-repository/` são diretórios irmãos, o caminho padrão aponta para `../config-repository`. Prepare a pasta local e crie o arquivo completo `config-repository/learning-catalog.yaml`:

```bash
mkdir -p ../config-repository
```

```yaml
# config-repository/learning-catalog.yaml
server:
  port: 8081
```

Execute o comando dentro do diretório do projeto `config-server`. Ele sobe o servidor na porta `8888`:

```bash
./gradlew bootRun
```

Antes de iniciar o cliente, faça este smoke test. Ele não chama a API de cursos. Ele pergunta ao Config Server qual configuração foi encontrada para a aplicação `learning-catalog` no perfil `default`:

```http
# config-server/src/main/resources/requests.http
GET http://localhost:8888/learning-catalog/default
```

A resposta vem em JSON, não como o YAML puro. Dentro de `propertySources`, ela deve conter `server.port: 8081`, vindo do arquivo `config-repository/learning-catalog.yaml`. Só depois dessa confirmação faz sentido iniciar o `learning-catalog`.

Agora crie um projeto novo chamado `learning-catalog`. O client puxa essa configuração na subida:

```kotlin
dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.3")
    }
}

implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.cloud:spring-cloud-starter-config")
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

A aplicação desse projeto tem sua própria classe principal:

```java
package com.example.learningcatalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LearningCatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningCatalogApplication.class, args);
    }
}
```

Nesta primeira extração, não vamos recriar persistência nem o evento do monólito. Um controller mínimo já permite testar a fronteira do serviço e será suficiente para o enrollment e o gateway:

```java
package com.example.learningcatalog;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/courses")
public class CourseCatalogController {

    @GetMapping
    public List<CourseSummary> list() {
        return List.of(
                new CourseSummary(1L, "Arquitetura de sistemas"));
    }

    public record CourseSummary(Long id, String title) {
    }
}
```

Essa classe pertence ao processo `learning-catalog`; não é compartilhada com `learning-monolith` e não usa `@ApplicationModuleListener`.

```yaml
# learning-catalog/src/main/resources/application.yaml
spring:
  application:
    name: learning-catalog
  config:
    import: configserver:http://localhost:8888
```

`spring.config.import` é a propriedade do Spring Boot que declara uma fonte externa de configuração. O prefixo `configserver:` indica que essa fonte é o Config Server. O client procura `learning-catalog.yaml` no Config Server. Com o import sem `optional`, o serviço falha na subida se o Config Server estiver indisponível, em vez de iniciar com configuração desconhecida. Nesse ponto, o catálogo é um projeto independente na porta `8081`; o registro no Eureka só será ativado quando a configuração remota receber a seção `eureka.client`.

Com o Config Server ainda rodando, execute `./gradlew bootRun` dentro do diretório do `learning-catalog`. O serviço consulta o Config Server na inicialização, recebe `server.port: 8081` e expõe sua própria API:

```http
# learning-catalog/src/main/resources/requests.http
GET http://localhost:8081/api/courses
```

Esse request confirma a primeira etapa distribuída: o catálogo é um processo separado e recebeu sua porta de uma fonte externa. Ainda não existe discovery nem gateway nessa etapa.

### Discovery

Configuração resolve “com quais valores eu inicio?”. Discovery resolve “onde está o serviço que preciso chamar?”. Em vez de fixar `http://192.168.1.20:8081`, o serviço se registra com um nome e o cliente procura esse nome no registry.

O segundo projeto de infraestrutura é o `eureka-server`, na porta `8761`. Eureka registra as instâncias e resolve pelo nome do serviço, sem exigir que os clientes conheçam IPs fixos. O server usa estas dependências completas:

```kotlin
dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.3")
    }
}

implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-server")
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

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

Mantenha este arquivo completo em `eureka-server/src/main/resources/application.yaml`:

```yaml
# eureka-server/src/main/resources/application.yaml
server:
  port: 8761
eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
```

Execute `./gradlew bootRun` dentro do diretório do `eureka-server` e confirme que o painel responde em `http://localhost:8761`. Agora atualize o bloco de dependências do `learning-catalog` para incluir o client do Eureka:

```kotlin
dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.3")
    }
}

implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.cloud:spring-cloud-starter-config")
implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

Essa é a composição completa do catálogo depois da introdução de discovery; não é uma dependência solta fora do projeto.

O Eureka também expõe uma API HTTP. Use este arquivo para verificar o registry:

```http
# eureka-server/src/main/resources/requests.http
GET http://localhost:8761/eureka/apps
```

Com apenas o Eureka ligado, a resposta `200` pode vir com uma lista vazia. Depois que o `learning-catalog` reiniciar com a configuração de Eureka, a mesma requisição deve listar `LEARNING-CATALOG`.

Substitua o arquivo `config-repository/learning-catalog.yaml` pelo arquivo completo abaixo. A porta continua `8081`; apenas acrescentamos o registro no Eureka:

```yaml
# config-repository/learning-catalog.yaml
server:
  port: 8081
spring:
  application:
    name: learning-catalog
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

Reinicie o `learning-catalog` depois de alterar a configuração. Consulte `http://localhost:8761/eureka/apps` para observar a instância registrada. Discovery resolve o nome, mas não transforma uma chamada HTTP comum em chamada balanceada; um cliente que deseja usar `lb://` precisa também do Spring Cloud LoadBalancer.

### Circuit breaker

Quando o enrollment chama o catálogo, a falha deixa de ser uma exceção local. O catálogo pode estar lento, fora do ar ou respondendo com erro. Sem proteção, cada requisição ao enrollment fica presa esperando uma dependência que não vai responder.

O terceiro projeto distribuído é o `learning-enrollment`, na porta `8082`. Ele não é o módulo de matrículas do monólito: é uma aplicação nova que consulta o `learning-catalog` pela rede.

Sua classe principal também pertence ao próprio projeto:

```java
package com.example.learningenrollment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LearningEnrollmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningEnrollmentApplication.class, args);
    }
}
```

Retry resolve uma falha transitória; circuit breaker protege o serviço quando uma dependência permanece indisponível. Depois de acumular falhas, o circuito abre, para de chamar o catálogo e entrega uma resposta de contingência. Depois de um intervalo, permite uma chamada de teste.

As dependências completas do `learning-enrollment` são:

```kotlin
dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.3")
    }
}

implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-restclient")
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("org.springframework.boot:spring-boot-starter-aspectj")
implementation("org.springframework.cloud:spring-cloud-starter-config")
implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")
implementation("io.github.resilience4j:resilience4j-micrometer:2.4.0")
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

O `@LoadBalanced` permite usar o nome registrado no Eureka como host:

```java
package com.example.learningenrollment;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
public class EnrollmentClientConfiguration {

    @Bean
    @Primary
    RestClient.Builder directRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
```

O builder direto fica como `@Primary` para componentes internos, como o cliente HTTP do Eureka. O builder com `@LoadBalanced` fica reservado para chamadas que usam nomes de serviço, como `http://learning-catalog`.

O serviço chama o catálogo e devolve uma recomendação de contingência quando o circuito abre:

```java
package com.example.learningenrollment;

import java.util.List;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class CourseRecommendationService {

    private final RestClient restClient;

    public CourseRecommendationService(
            @Qualifier("loadBalancedRestClientBuilder") RestClient.Builder builder) {
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
package com.example.learningenrollment;

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

Crie o arquivo completo `config-repository/learning-enrollment.yaml`:

```yaml
# config-repository/learning-enrollment.yaml
server:
  port: 8082
spring:
  application:
    name: learning-enrollment
  threads:
    virtual:
      enabled: true
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
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

O `learning-enrollment` também busca configuração no Config Server. Mantenha este arquivo local apenas com a identidade do cliente e o import da fonte externa:

```yaml
# learning-enrollment/src/main/resources/application.yaml
spring:
  application:
    name: learning-enrollment
  config:
    import: configserver:http://localhost:8888
```

Com o `config-server`, o `eureka-server` e o `learning-catalog` rodando, execute `./gradlew bootRun` dentro do diretório do `learning-enrollment`.

Use este request para testar o enrollment enquanto o catálogo está disponível:

```http
# learning-enrollment/src/main/resources/requests.http
GET http://localhost:8082/api/recommendations
```

A resposta deve conter o curso retornado pelo `learning-catalog`. Se o catálogo estiver indisponível e o circuito entrar em fallback, a lista conterá `Catálogo indisponível`.

`failureRateThreshold: 50` abre o circuito quando metade das últimas dez chamadas falha. Para observar, pare o `learning-catalog` e deixe o `learning-enrollment` rodando. O enrollment continua disponível, mas a chamada ao catálogo falha e o Resilience4j registra essas falhas:

```bash
for attempt in {1..10}; do
  curl http://localhost:8082/api/recommendations
done
```

Depois do limite, a resposta passa a ser `Catálogo indisponível` sem tentar chamar o catálogo a cada requisição. O endpoint `/actuator/metrics/resilience4j.circuitbreaker.calls` mostra as chamadas do circuito quando o Actuator estiver exposto. Religue o catálogo e aguarde os 30 segundos de `waitDurationInOpenState` para a chamada de teste.

### Gateway

O cliente externo não deveria conhecer cada serviço nem suas portas internas. O gateway concentra a entrada HTTP e aplica regras de borda antes de encaminhar a requisição.

O quarto projeto distribuído é o `learning-gateway`, na porta `8084`. O gateway é a porta única de entrada: recebe a requisição externa, roteia para o serviço interno pelo nome do discovery e aplica filtros (auth, rate limit, rewrite). A porta `8080` continua pertencendo ao `learning-monolith`; o gateway não reutiliza essa porta.

O Spring Cloud Gateway roda em cima do WebFlux:

```java
package com.example.learninggateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LearningGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningGatewayApplication.class, args);
    }
}
```

Neste ponto, `learning-catalog` e `learning-enrollment` já foram apresentados, implementados e registrados no Eureka. Agora o gateway pode declarar as duas rotas sem apontar para um serviço ainda não criado.

Roteamento por configuração externa:

```yaml
# config-repository/learning-gateway.yaml
server:
  port: 8084
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
                - Path=/api/recommendations/**
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

O `learning-gateway` também importa o Config Server. Seu arquivo local contém apenas a identidade da aplicação e a origem da configuração:

```yaml
# learning-gateway/src/main/resources/application.yaml
spring:
  application:
    name: learning-gateway
  config:
    import: configserver:http://localhost:8888
```

`lb://learning-catalog` e `lb://learning-enrollment` resolvem os serviços pelo discovery e balanceiam entre as instâncias. O cliente externo conhece o gateway em `8084`; os serviços internos ficam escondidos. Mantenha `eureka-server`, `config-server`, `learning-catalog`, `learning-enrollment` e `learning-gateway` rodando nesta etapa:

```http
### Catálogo pelo gateway
GET http://localhost:8084/api/courses

### Recomendações pelo gateway
GET http://localhost:8084/api/recommendations
```

O primeiro request chega ao `CourseCatalogController` do `learning-catalog`. O segundo chega ao `RecommendationController` do `learning-enrollment`, que consulta o catálogo usando o `RestClient`. Os dois requests entram pela mesma porta, mas continuam atendendo processos diferentes.

Filtros entram por rota: rewrite de path, header extra e rate limit por usuário. As dependências completas do `learning-gateway` são:

```kotlin
dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.3")
    }
}

implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webflux")
implementation("org.springframework.cloud:spring-cloud-starter-config")
implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webflux")
implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
implementation("org.springframework.cloud:spring-cloud-starter-loadbalancer")
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

O gateway roda em cima do WebFlux (o starter traz o `webflux`), mesmo que os serviços atrás sejam MVC; ele só encaminha.

Com `config-server`, `eureka-server`, `learning-catalog` e `learning-enrollment` rodando, execute `./gradlew bootRun` dentro do diretório do `learning-gateway`. Depois, envie os dois requests mostrados acima.

## MVC + virtual threads vs WebFlux

A distribuição dos serviços não obriga todos eles a usarem o mesmo modelo HTTP. Para o catálogo e o enrollment, o código é síncrono: JPA e `RestClient` são bloqueantes. MVC com virtual threads combina com esse domínio e reduz o custo de esperar I/O sem exigir que tudo vire reativo.

As virtual threads já estão habilitadas na configuração remota completa do `learning-enrollment`. Uma virtual thread ainda consome memória e ainda pode segurar conexão de banco ou socket. Ela não transforma uma chamada bloqueante em não bloqueante; apenas evita ocupar uma thread de plataforma durante parte da espera.

WebFlux é outra escolha, não uma evolução automática de MVC. Ele faz sentido quando o fluxo reativo atravessa a aplicação inteira, como um serviço de streaming de progresso. Para comparar sem misturar configurações, esse serviço será outro projeto, separado do catálogo:

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webflux")
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

Crie um projeto novo chamado `learning-stream`. A aplicação é independente, usa a porta `8083` e não altera o `learning-monolith` nem o `learning-catalog`.

```java
package com.example.learningstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class LearningStreamApplication {

    public static void main(String[] args) {
        SpringApplication.run(LearningStreamApplication.class, args);
    }
}
```

Mantenha este arquivo completo em `learning-stream/src/main/resources/application.yaml`:

```yaml
# learning-stream/src/main/resources/application.yaml
spring:
  application:
    name: learning-stream
server:
  port: 8083
```

Execute `./gradlew bootRun` dentro do diretório do `learning-stream`.

```java
package com.example.learningstream;

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

Use este request para abrir o stream no cliente HTTP:

```http
# learning-stream/src/main/resources/requests.http
GET http://localhost:8083/api/progress
```

Observe o streaming sem o cliente esperar o corpo inteiro:

```bash
curl -N http://localhost:8083/api/progress
```

WebFlux exige repositórios, clientes e operadores reativos de ponta a ponta. `block()` escondido devolve o gargalo que o modelo reativo tentava evitar. Para CRUD com JPA e chamadas HTTP bloqueantes, MVC com virtual threads mantém o código menor e o diagnóstico mais direto.

Ao comparar as abordagens, o `learning-monolith` continua sendo o projeto MVC modular em `8080`; `learning-catalog` e `learning-enrollment` são serviços MVC independentes; `learning-stream` é o serviço WebFlux independente em `8083`. Nenhum projeto troca sua porta durante a aula.

## Decisões que ficam

O `learning-monolith` é a escolha adequada quando os módulos pertencem ao mesmo produto, podem compartilhar um deploy e não precisam escalar ou ser operados separadamente. O Modulith transforma essa organização em regras verificáveis, e o evento nomeado deixa explícita a API entre `catalog` e `enrollment`.

Os projetos distribuídos mostram o custo adicional da separação: configuração remota, registro de serviços, gateway, chamadas HTTP e falhas de rede. Eles fazem sentido quando o ganho de independência compensa essa operação. Dentro de cada processo, MVC com virtual threads atende fluxos bloqueantes; WebFlux atende o serviço de streaming quando o fluxo reativo é uma decisão de ponta a ponta.

## Ordem de execução

Execute os projetos em grupos separados. Para entender o monólito, basta iniciar `learning-monolith` em `8080`. Para testar a versão distribuída, pare ou deixe o monólito fora desse fluxo e inicie os projetos nesta ordem:

Antes de iniciar os clientes, confirme que `config-repository/learning-catalog.yaml`, `config-repository/learning-enrollment.yaml` e `config-repository/learning-gateway.yaml` existem. O `config-server` entrega esses arquivos pelo nome de cada aplicação.

1. `config-server` em `8888`;
2. `eureka-server` em `8761`;
3. `learning-catalog` em `8081`;
4. `learning-enrollment` em `8082`;
5. `learning-gateway` em `8084`.

O `learning-stream` em `8083` é independente e pode ser iniciado separado. A ordem existe porque os serviços dependem dos recursos anteriores: o catálogo precisa do Config Server e do Eureka; o enrollment precisa encontrar o catálogo; o gateway precisa encontrar catálogo e enrollment. Nenhum desses projetos altera a porta ou o código do `learning-monolith`.

## Estrutura

```
learning-monolith/
├── src/main/java/com/example/learningmonolith/
│   ├── LearningMonolithApplication.java
│   ├── catalog/
│   │   ├── CatalogService.java
│   │   ├── CourseCatalogController.java
│   │   └── events/
│   │       ├── CoursePublished.java
│   │       └── package-info.java
│   └── enrollment/
│       ├── CoursePublicationListener.java
│       └── package-info.java
├── src/main/resources/
│   ├── application.yaml
│   └── requests.http
└── src/test/java/com/example/learningmonolith/
    └── ArchitectureTest.java
config-server/
├── src/main/java/com/example/configserver/
│   └── ConfigServerApplication.java
└── src/main/resources/
    ├── application.yaml
    └── requests.http
config-repository/
├── learning-catalog.yaml
├── learning-enrollment.yaml
└── learning-gateway.yaml
eureka-server/
├── src/main/java/com/example/eurekaserver/
│   └── EurekaServerApplication.java
└── src/main/resources/
    ├── application.yaml
    └── requests.http
learning-catalog/
├── src/main/java/com/example/learningcatalog/
│   ├── LearningCatalogApplication.java
│   └── CourseCatalogController.java
└── src/main/resources/
    ├── application.yaml
    └── requests.http
learning-enrollment/
├── src/main/java/com/example/learningenrollment/
│   ├── LearningEnrollmentApplication.java
│   ├── CourseRecommendationService.java
│   ├── EnrollmentClientConfiguration.java
│   └── RecommendationController.java
└── src/main/resources/
    ├── application.yaml
    └── requests.http
learning-gateway/
├── src/main/java/com/example/learninggateway/
│   └── LearningGatewayApplication.java
└── src/main/resources/
    └── application.yaml
learning-stream/
├── src/main/java/com/example/learningstream/
│   ├── ProgressStreamController.java
│   └── LearningStreamApplication.java
└── src/main/resources/
    ├── application.yaml
    └── requests.http
```
