# Aula 01 — Setup moderno do Spring Boot

Objetivo: montar um projeto Spring Boot 4.1 do zero com Gradle e entender o que mudou na geração 4.

## Versões

- Spring Boot 4.1 em cima do Spring Framework 7.
- Java 25 LTS.
- Jakarta EE 11.
- Jackson 3.

## Starters modulares

O autoconfigure virou 70+ módulos. Cada starter puxa só o que precisa. Nomes mudaram e todo starter tem um companheiro de teste.

| Uso | Starter |
|---|---|
| Web MVC | `spring-boot-starter-webmvc` |
| Cliente REST | `spring-boot-starter-restclient` |
| WebClient reativo | `spring-boot-starter-webclient` |
| JPA | `spring-boot-starter-data-jpa` |
| Teste de web | `spring-boot-starter-webmvc-test` |

## Initializr

O projeto vem do start.spring.io. Você marca Spring Boot 4.1, Java 25, Gradle e os starters que quer. Ele gera a estrutura e o `build.gradle.kts`.

No IntelliJ, o wizard Spring Boot (File → New → Project → Spring Boot) faz a mesma coisa: fala com a API do start.spring.io e já abre o projeto direto na IDE. Requer a versão Ultimate. Em projeto existente, o inlay "Add Starters" no `build.gradle.kts` adiciona dependência respeitando a versão do Boot.

Os campos `Group`, `Artifact` e `Name` usam kebab-case minúsculo, ex: `first-project`. O `Name` vira o `rootProject.name` do Gradle, então nada de espaço ou maiúscula.

## Projeto do zero

`build.gradle.kts`:

```kotlin
plugins {
    id("java")
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

O plugin `io.spring.dependency-management` aplica a BOM do Boot. Com ele, nenhuma dependência precisa de versão.

O `spring-boot-starter-webmvc-test` já traz o JUnit Jupiter. O `junit-platform-launcher` em `testRuntimeOnly` é o motor que o Gradle usa pra executar os testes.

O arquivo usa Kotlin DSL (`.kts`), o padrão atual do Gradle. Groovy DSL (`.gradle`) ainda existe, mas projeto novo vai de Kotlin: tipado, com autocomplete e refactor melhores na IDE.

## Aplicação principal

```java
package com.example.firstproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FirstProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(FirstProjectApplication.class, args);
    }
}
```

`@SpringBootApplication` junta `@Configuration`, `@EnableAutoConfiguration` e `@ComponentScan` num só lugar.

## JSpecify

O Framework 7 anota as APIs com JSpecify. Você marca o seu código do mesmo jeito. Declare null-safe por pacote com `package-info.java`:

```java
@NullMarked
package com.example.firstproject.greeting;

import org.jspecify.annotations.NullMarked;
```

Dentro do pacote, tudo é `@NonNull` por padrão. Onde pode chegar `null`, anote `@Nullable`:

```java
package com.example.firstproject.greeting;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class GreetingFormatter {

    public String formatWithSuffix(String message, @Nullable String suffix) {
        return suffix == null ? message : message + " " + suffix;
    }
}
```

Isso vira contrato no compilador. IDE e ferramentas estáticas reclamam se você passa `null` onde não pode.

`@Component` marca o `GreetingFormatter` como bean. O controller o recebe pelo construtor.

## Controller de exemplo

```java
package com.example.firstproject.web;

import java.time.Instant;

import com.example.firstproject.greeting.GreetingFormatter;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/greetings")
public class GreetingController {

    private final GreetingFormatter formatter;

    public GreetingController(GreetingFormatter formatter) {
        this.formatter = formatter;
    }

    @GetMapping
    public GreetingResponse greet() {
        String message = formatter.formatWithSuffix("Hello, world!", null);
        return new GreetingResponse(message, Instant.now());
    }

    public record GreetingResponse(String message, Instant timestamp) {
    }
}
```

`Instant` é um ponto no tempo em UTC, o tipo certo pra timestamp de API.

## Rodando

```bash
./gradlew bootRun
```

Para gerar o jar executável:

```bash
./gradlew bootJar
java -jar build/libs/first-project-0.0.1-SNAPSHOT.jar
```

Packaging é `jar`: servidor embutido, roda com `java -jar`. `war` só faz sentido pra deploy num servlet container externo (Tomcat standalone, WebSphere, legado).

## DevTools

`spring-boot-devtools` reinicia a aplicação sozinho quando você salva um arquivo (restart de classpath) e desliga o cache de templates em dev. Não vai pro jar de produção.

## application.yaml

```yaml
spring:
  application:
    name: first-project
server:
  port: 8080
```

Fica em `src/main/resources`. É aqui que você liga ou desliga o que os starters trazem e ajusta porta, contexto e afins.

O formato YAML (`application.yaml`) é o mais comum pra config hierárquica. O wizard ainda gera `application.properties`, que serve pra config flat e simples. Use um formato só: misturar os dois no mesmo projeto dá prioridade ao `.properties` e vira confusão.

## Jackson 3 e Jakarta EE 11

Jackson 3 mudou de pacote. Nada de `com.fasterxml.jackson`, agora é `tools.jackson`:

```java
import tools.jackson.databind.JsonMapper;
```

O bean que o Boot configura pra JSON é o `JsonMapper` (imutável), no lugar do antigo `ObjectMapper`. Exceção: `jackson-annotations` segue em `com.fasterxml.jackson.annotation`, então `@JsonProperty` e afins continuam funcionando sem mexer no import.

O servlet, JPA e Bean Validation vieram de Jakarta EE 10 para 11. No código seu isso não muda nada, mas as dependências transitivas sobem: Servlet 6.1, JPA 3.2, Bean Validation 3.1.

## AutoConfiguration.imports

Desde o Boot 3.0, auto-configuração não usa mais `spring.factories`. Cada módulo declara as suas em:

```
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

O arquivo lista uma classe de configuração por linha:

```
org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration
org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
```

O Boot lê esse arquivo de todos os jars do classpath, aplica as condições e cria os beans. Sua aplicação só declara os starters.

No Boot 4, o `spring-boot-autoconfigure` monolítico virou módulos. As classes de auto-configuração mudaram de pacote: saíram de `org.springframework.boot.autoconfigure.*` pra `org.springframework.boot.<módulo>.autoconfigure.*`.

## Estrutura

O projeto completo, montado ao longo da aula:

```
src/main/java/com/example/firstproject/
├── FirstProjectApplication.java
├── greeting/
│   ├── GreetingFormatter.java
│   └── package-info.java
└── web/
    └── GreetingController.java
src/main/resources/
└── application.yaml
```