# Aula 10 — Produção e deploy

Objetivo: configurar por ambiente, lidar com secrets e empacotar em jar, container ou native image.

## Profiles e config externa

Base do projeto:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

O `webmvc` roda a API; o `actuator` expõe os endpoints de health que o Kubernetes usa mais abaixo.

Cada ambiente tem seu arquivo. O `application.yaml` carrega o padrão; os `application-{profile}.yaml` sobrescrevem por perfil.

```yaml
# application.yaml
spring:
  application:
    name: books
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
```

```yaml
# application-prod.yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USER}
    password: ${DATABASE_PASSWORD}
```

O perfil ativo vem de variável de ambiente, com `dev` como fallback. Em produção, `SPRING_PROFILES_ACTIVE=prod`.

A precedência, da maior pra menor:

1. Argumentos de linha de comando (`--server.port=9090`).
2. Variáveis de ambiente (`SERVER_PORT=9090`).
3. Arquivos `application-{profile}.yaml`.
4. `application.yaml`.

O Spring mapeia env vars relaxadas: `SPRING_DATASOURCE_URL` vira `spring.datasource.url`. Pra um bloco inteiro de config, `SPRING_APPLICATION_JSON` aceita JSON:

```bash
SPRING_APPLICATION_JSON='{"spring":{"datasource":{"url":"jdbc:postgresql://db:5432/books"}}}' java -jar app.jar
```

## Secrets

Secrets nunca vão no código nem no repositório. A aplicação lê de variável de ambiente:

```yaml
app:
  security:
    jwt-secret: ${JWT_SECRET}
```

No Kubernetes, o `Secret` alimenta a variável; fora dele, o ambiente já provê. Pra secret rotacionado e centralizado, um cofre como HashiCorp Vault entra como backend de config, sem mudar o jeito de a aplicação ler (continua sendo propriedade).

## GraalVM native image + AOT

O Boot 4 faz AOT na compilação e gera um executável nativo, com startup em milissegundos e memória menor. O plugin:

```kotlin
plugins {
    id("org.springframework.boot") version "4.1.0"
    id("org.graalvm.buildtools.native") version "0.11.5"
}
```

Com o plugin native aplicado, o AOT roda automaticamente:

```bash
./gradlew nativeCompile
```

O executável sai em `build/native/nativeCompile`. Pra imagem de container nativa, direto com buildpacks:

```bash
./gradlew bootBuildImage
```

O preço é o closed-world: o GraalVM analisa o código a partir do `main` e precisa ver toda classe, método e recurso em build-time. Reflexão e proxy dinâmicos que o Spring não prevê viram hints em `META-INF/native-image`. O Boot gera a maioria sozinho; o que sobra, o tracing agent do GraalVM descobre rodando a aplicação.

O Boot 4 exige GraalVM 25 (baseline Java 25). Native image não é default: brilha em serverless e scale-to-zero, onde startup e memória dominam. Serviço que roda sempre, com JIT aquecido, rende mais no JVM.

## Docker

Caminho mais simples é buildpacks, sem Dockerfile:

```bash
./gradlew bootBuildImage
```

Imagem pronta, com camadas otimizadas. Se preferir Dockerfile clássico:

```dockerfile
FROM eclipse-temurin:25-jre
COPY build/libs/books-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Kubernetes e health checks

O Actuator alimenta as probes:

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 15
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
```

Liveness falhou, o pod é reiniciado. Readiness falhou, o pod sai do Service, sem ser morto. As duas apontam pra endpoints diferentes de propósito: um diz "não vou voltar" e o outro diz "ainda não tô pronto".

## Estrutura

```
src/main/resources/
├── application.yaml
└── application-prod.yaml
build.gradle.kts
Dockerfile
k8s/
└── deployment.yaml
```
