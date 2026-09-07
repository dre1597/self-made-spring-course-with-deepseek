# Aula 02 — IoC e Dependency Injection

Objetivo: entender o container, o que é bean e como a injeção por construtor resolve o grafo de dependências.

## Container e Inversão de Controle

O `ApplicationContext` é o container. Você declara o que precisa; o Spring cria, injeta e gerencia os objetos.

Inversão de controle: o `new` não é seu. Você descreve as dependências e o container monta o grafo quando a aplicação sobe. Se um bean falta, o contexto nem inicia e você vê o erro já na subida.

A classe principal:

```java
package com.example.greetings;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GreetingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GreetingServiceApplication.class, args);
    }
}
```

`@SpringBootApplication` liga o `@ComponentScan`, que varre o pacote `com.example.greetings` atrás de classes anotadas e as registra como beans. O `@ConfigurationPropertiesScan` será explicado na última seção.

## Beans e estereótipos

Bean é um objeto que o Spring cria e administra. O jeito mais comum de declarar é anotar a própria classe com um estereótipo.

Começamos com uma interface de saudação e duas implementações:

```java
package com.example.greetings.greeting;

public interface GreetingService {

    String greet(String name);
}
```

```java
package com.example.greetings.greeting;

import org.springframework.stereotype.Service;

@Service
public class EnglishGreetingService implements GreetingService {

    @Override
    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
```

```java
package com.example.greetings.greeting;

import org.springframework.stereotype.Service;

@Service
public class PortugueseGreetingService implements GreetingService {

    @Override
    public String greet(String name) {
        return "Olá, " + name + "!";
    }
}
```

Um formatter como bean genérico:

```java
package com.example.greetings.greeting;

import org.springframework.stereotype.Component;

@Component
public class GreetingFormatter {

    public String format(String message, String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return message;
        }
        return message + " " + suffix;
    }
}
```

Os estereótipos todos derivam de `@Component` e só comunicam o papel:

| Anotação | Papel |
|---|---|
| `@Component` | bean genérico |
| `@Service` | camada de serviço, marca intenção |
| `@Repository` | acesso a dados, traduz exceções de persistência |
| `@Configuration` | fonte de `@Bean` |
| `@RestController` | controller REST (`@Controller` + `@ResponseBody`) |

## Múltiplos candidatos

Agora existem dois beans do tipo `GreetingService`. Injetar o tipo puro falha na subida: o Spring acha dois candidatos e não sabe qual usar.

Primeira saída: `@Primary` marca o padrão.

```java
package com.example.greetings.greeting;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class PortugueseGreetingService implements GreetingService {

    @Override
    public String greet(String name) {
        return "Olá, " + name + "!";
    }
}
```

Segunda saída: `@Qualifier` escolhe pelo nome do bean no ponto de injeção. O nome default é o da classe com a primeira letra minúscula. Um controller que quer o inglês especificamente:

```java
package com.example.greetings.web;

import com.example.greetings.greeting.GreetingService;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/greetings/en")
public class EnglishGreetingController {

    private final GreetingService greetingService;

    public EnglishGreetingController(@Qualifier("englishGreetingService") GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping
    public String greet() {
        return greetingService.greet("world");
    }
}
```

## @Bean vs @Component

`@Component` é pra classe que você escreveu. `@Bean` é pra objeto de lib de terceiros, quando você não controla a classe, ou quando a criação tem lógica.

`Clock` vem do JDK. Você não anota a classe com `@Component`, então expõe via `@Bean`:

```java
package com.example.greetings.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {

    @Bean
    Clock systemUtcClock() {
        return Clock.systemUTC();
    }
}
```

O método `systemUtcClock()` vira um bean do tipo `Clock`, disponível pra injeção em qualquer outro bean.

## Ciclo de vida

O container cria o bean, injeta as dependências e chama os callbacks de inicialização. No desligamento, chama os de destruição.

```java
package com.example.greetings.greeting;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GreetingServiceLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(GreetingServiceLifecycle.class);

    @PostConstruct
    void logStartup() {
        logger.info("GreetingServiceLifecycle inicializado");
    }

    @PreDestroy
    void logShutdown() {
        logger.info("GreetingServiceLifecycle encerrado");
    }
}
```

`@PostConstruct` roda depois que o bean está totalmente montado, com as dependências injetadas. `@PreDestroy` roda quando o contexto fecha. São de `jakarta.annotation` e são o caminho padrão, sem acoplar o código a interfaces do Spring.

## Injeção por construtor

O Spring olha o construtor, resolve cada parâmetro pelo tipo e injeta. Com um único construtor, não precisa de `@Autowired`.

O controller principal junta tudo:

```java
package com.example.greetings.web;

import java.time.Clock;
import java.time.Instant;

import com.example.greetings.config.GreetingProperties;
import com.example.greetings.greeting.GreetingFormatter;
import com.example.greetings.greeting.GreetingService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/greetings")
public class GreetingController {

    private final GreetingService greetingService;
    private final GreetingFormatter formatter;
    private final GreetingProperties properties;
    private final Clock clock;

    public GreetingController(GreetingService greetingService, GreetingFormatter formatter,
            GreetingProperties properties, Clock clock) {
        this.greetingService = greetingService;
        this.formatter = formatter;
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping
    public GreetingResponse greet() {
        String message = formatter.format(greetingService.greet("mundo"), properties.suffix());
        return new GreetingResponse(message, Instant.now(clock));
    }

    public record GreetingResponse(String message, Instant timestamp) {
    }
}
```

Construtor dá campo `final` (imutável), teste fácil (você passa os mocks no `new`) e nenhuma reflexão em campo.

## ConfigurationProperties

Liga propriedades externas a um tipo fortemente tipado, em vez de espalhar `@Value`.

```yaml
greeting:
  default-language: pt
  suffix: "!"
```

```java
package com.example.greetings.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "greeting")
public record GreetingProperties(String defaultLanguage, String suffix) {
}
```

Record com construtor único faz binding por construtor. O `@ConfigurationPropertiesScan` na classe principal registra o bean, que chega preenchido com o que está no `application.yaml`.

Estrutura até aqui:

```
src/main/java/com/example/greetings/
├── GreetingServiceApplication.java
├── config/
│   ├── ClockConfiguration.java
│   └── GreetingProperties.java
├── greeting/
│   ├── GreetingService.java
│   ├── EnglishGreetingService.java
│   ├── PortugueseGreetingService.java
│   ├── GreetingFormatter.java
│   └── GreetingServiceLifecycle.java
└── web/
    ├── GreetingController.java
    └── EnglishGreetingController.java
```

## Scopes

Controla quantas instâncias o container mantém.

| Scope | Instâncias |
|---|---|
| `singleton` | uma por container (default) |
| `prototype` | uma nova a cada solicitação |
| `request` | uma por requisição HTTP |
| `session` | uma por sessão HTTP |

Singleton é o que você quer na imensa maioria dos casos. Prototype serve quando cada uso precisa de estado próprio.

Um gerador de relatório prototype, que guarda o instante em que foi criado:

```java
package com.example.reports.report;

import java.time.Instant;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class ReportGenerator {

    private final Instant startedAt = Instant.now();

    public Report generate(String title, String content) {
        return new Report(title, content, startedAt);
    }
}
```

```java
package com.example.reports.report;

import java.time.Instant;

public record Report(String title, String content, Instant startedAt) {
}
```

Injetar um prototype direto num singleton daria uma instância só, criada na injeção. Pra pedir uma nova a cada uso, injete `ObjectProvider` e chame `getObject()`:

```java
package com.example.reports.report;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final ObjectProvider<ReportGenerator> reportGenerators;
    private final ReportWriter reportWriter;

    public ReportService(ObjectProvider<ReportGenerator> reportGenerators, ReportWriter reportWriter) {
        this.reportGenerators = reportGenerators;
        this.reportWriter = reportWriter;
    }

    public Report generateAndWrite(String title, String content) {
        ReportGenerator generator = reportGenerators.getObject();
        Report report = generator.generate(title, content);
        reportWriter.write(report);
        return report;
    }
}
```

Cada `getObject()` entrega um `ReportGenerator` novo, com `startedAt` próprio.

## Profiles

Agrupa beans por ambiente. Um bean com `@Profile("dev")` só existe quando o perfil `dev` está ativo.

Uma interface de escrita de relatório com duas implementações:

```java
package com.example.reports.report;

public interface ReportWriter {

    void write(Report report);
}
```

```java
package com.example.reports.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class ConsoleReportWriter implements ReportWriter {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleReportWriter.class);

    @Override
    public void write(Report report) {
        logger.info("Relatório '{}' ({}): {}", report.title(), report.startedAt(), report.content());
    }
}
```

```java
package com.example.reports.report;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class FileReportWriter implements ReportWriter {

    @Override
    public void write(Report report) {
        Path destination = Path.of("build", "reports", report.title() + ".txt");
        try {
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, report.content());
        } catch (IOException exception) {
            throw new IllegalStateException("Não conseguiu gravar o relatório em " + destination, exception);
        }
    }
}
```

Com `dev` ativo, só o `ConsoleReportWriter` existe. Com `prod`, só o `FileReportWriter`.

Um runner que usa o serviço na subida, pra exercitar o fluxo completo:

```java
package com.example.reports.report;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ReportStartupRunner implements ApplicationRunner {

    private final ReportService reportService;

    public ReportStartupRunner(ReportService reportService) {
        this.reportService = reportService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        reportService.generateAndWrite("relatorio-diario", "Vendas do dia: 42 pedidos");
    }
}
```

`ApplicationRunner.run()` roda depois que o contexto sobe. A classe principal:

```java
package com.example.reports;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReportServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportServiceApplication.class, args);
    }
}
```

Perfil ativo no `application.yaml`:

```yaml
spring:
  profiles:
    active: dev
```

Troque pra `prod` e o relatório passa a ser gravado em arquivo. Se nenhum perfil estiver ativo, a subida falha: `ReportService` não acha um `ReportWriter`.

Estrutura:

```
src/main/java/com/example/reports/
├── ReportServiceApplication.java
└── report/
    ├── Report.java
    ├── ReportGenerator.java
    ├── ReportWriter.java
    ├── ConsoleReportWriter.java
    ├── FileReportWriter.java
    ├── ReportService.java
    └── ReportStartupRunner.java
```
