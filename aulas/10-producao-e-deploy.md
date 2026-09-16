# Aula 10 — Produção e deploy

Objetivo: configurar uma aplicação por ambiente, manter secrets fora do código e empacotar o mesmo serviço como jar, container ou native image.

Domínio: um observatório meteorológico que recebe leituras de estações e expõe o estado do serviço. O projeto é próprio desta aula e não depende dos projetos das aulas anteriores.

## Base do projeto

O projeto-base já fornece o contexto, a API HTTP e os endpoints de operação:

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

O `spring-boot-starter` fornece o contexto da aplicação. O `webmvc` roda a API do observatório. O `actuator` expõe health checks e métricas operacionais.

A aplicação principal:

```java
package com.example.observatory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ObservatoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ObservatoryApplication.class, args);
    }
}
```

## Profiles e config externa

Cada ambiente tem seu arquivo. O `application.yaml` carrega o padrão; os `application-{profile}.yaml` sobrescrevem por perfil.

```yaml
# application.yaml
spring:
  application:
    name: observatory
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
app:
  observatory:
    station-name: local
    reading-interval: 60s
```

```yaml
# application-prod.yaml
spring:
  config:
    activate:
      on-profile: prod
app:
  observatory:
    station-name: central
    reading-interval: 30s
```

O perfil ativo vem de variável de ambiente, com `dev` como fallback. Em produção, `SPRING_PROFILES_ACTIVE=prod`.

A precedência, da maior pra menor:

1. Argumentos de linha de comando (`--server.port=9090`).
2. Variáveis de ambiente (`SERVER_PORT=9090`).
3. Arquivos `application-{profile}.yaml`.
4. `application.yaml`.

O Spring mapeia env vars relaxadas: `APP_OBSERVATORY_STATION_NAME` vira `app.observatory.station-name`. Pra um bloco inteiro de config, `SPRING_APPLICATION_JSON` aceita JSON:

```bash
SPRING_APPLICATION_JSON='{"app":{"observatory":{"station-name":"north-platform"}}}' java -jar app.jar
```

Para consumir configuração da aplicação, prefira binding tipado a espalhar `@Value` pelo código:

```java
package com.example.observatory.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.observatory")
public record ObservatoryProperties(String stationName, Duration readingInterval) {
}
```

O endpoint deixa o valor carregado observável durante a aula:

```java
package com.example.observatory.web;

import java.time.Duration;

import com.example.observatory.config.ObservatoryProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ObservatoryStatusController {

    private final ObservatoryProperties properties;

    public ObservatoryStatusController(ObservatoryProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/status")
    public Status status() {
        return new Status(properties.stationName(), properties.readingInterval());
    }

    public record Status(String stationName, Duration readingInterval) {
    }
}
```

Com o perfil `dev`, `GET /api/status` retorna `local` e `60s`. Com `SPRING_PROFILES_ACTIVE=prod`, retorna `central` e `30s`. O mesmo código atende os dois ambientes; só a configuração muda.

```http
### Consulta a configuração carregada
GET http://localhost:8080/api/status
```

## Secrets

O observatório envia alertas para um provedor externo. A chave desse provedor é configuração sensível: ela não entra no código, no `application.yaml` versionado nem na imagem do container.

```yaml
app:
  alerting:
    provider-api-key: ${ALERTING_PROVIDER_API_KEY}
```

O binding tipado também vale para a configuração sensível. O código recebe a chave sem precisar logá-la ou devolvê-la pela API:

```java
package com.example.observatory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.alerting")
public record AlertingProperties(String providerApiKey) {
}
```

Se `ALERTING_PROVIDER_API_KEY` não existir, o placeholder sem valor padrão impede o binding e a aplicação não sobe com uma credencial ausente. Em desenvolvimento, defina a variável no ambiente antes de executar:

```bash
export ALERTING_PROVIDER_API_KEY=dev-only-key
./gradlew bootRun
```

No Kubernetes, um recurso `Secret` alimenta essa variável. Um cofre como HashiCorp Vault pode fornecer o valor e permitir rotação sem colocar a chave no repositório. A aplicação continua lendo uma propriedade; a origem do valor muda fora dela.

## GraalVM native image + AOT

O AOT (ahead-of-time) prepara o contexto do Spring em build time. A native image usa esse resultado para compilar a aplicação para um executável específico do sistema, com startup rápido e menor consumo de memória. O caminho exige GraalVM 25 e não substitui o jar da aplicação: você escolhe o artefato para cada ambiente.

Adicione o plugin de native image ao `build.gradle.kts`:

```kotlin
plugins {
    id("org.graalvm.buildtools.native") version "0.11.5"
}
```

Com o plugin aplicado, o Gradle expõe `nativeCompile`. A tarefa executa o AOT e chama o compilador nativo:

```bash
./gradlew nativeCompile
```

O executável sai em `build/native/nativeCompile`. Para criar uma imagem nativa com buildpacks, ative `BP_NATIVE_IMAGE`:

```bash
BP_NATIVE_IMAGE=true ./gradlew bootBuildImage
```

O closed world é a principal restrição: o GraalVM analisa o código a partir do `main` e precisa conhecer classes, métodos e recursos em build time. Reflexão e proxies dinâmicos que o Spring não prevê precisam de hints. O Boot gera muitos hints; integrações fora do suporte automático podem exigir configuração adicional ou o tracing agent do GraalVM.

Native image faz sentido em serverless e scale-to-zero, onde startup e memória pesam. Um serviço que roda continuamente pode continuar no JVM e aproveitar o JIT aquecido.

## Docker

O `bootJar` empacota a aplicação JVM em um arquivo executável. O buildpack transforma esse jar em uma imagem com camadas separadas para dependências, classes e recursos:

```bash
./gradlew bootJar
./gradlew bootBuildImage
```

Para o Dockerfile manual não depender da versão do projeto, fixe o nome do artefato no `build.gradle.kts`:

```kotlin
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("observatory.jar")
}
```

Esse comando cria uma imagem JVM sem exigir um Dockerfile escrito por você. Para controlar a imagem manualmente, use um Dockerfile:

```dockerfile
FROM eclipse-temurin:25-jre
COPY build/libs/observatory.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Construa e execute a imagem passando o perfil e o secret pelo ambiente:

```bash
docker build -t observatory:local .
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e ALERTING_PROVIDER_API_KEY=local-only-key \
  observatory:local
```

O Dockerfile contém o runtime e o jar, mas não contém a chave. A mesma imagem pode rodar em ambientes diferentes porque o ambiente injeta o perfil e os secrets.

## Kubernetes e health checks

O Actuator precisa publicar o health endpoint e habilitar os grupos de probes. Coloque isso no `application-prod.yaml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true
```

O Kubernetes usa liveness para decidir se o processo precisa reiniciar e readiness para decidir se o pod pode receber tráfego:

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
  periodSeconds: 10
```

Se liveness falhar, o pod é reiniciado. Se readiness falhar, o pod sai do Service sem ser morto. Neste projeto, o Actuator verifica apenas a saúde básica da aplicação; o provedor de alertas não entra no grupo de readiness porque o exemplo não implementa um cliente para ele. Em uma aplicação real, adicione um `HealthIndicator` antes de usar uma dependência externa como critério de readiness.

Um `Deployment` injeta o perfil e o secret sem alterar a imagem:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: observatory
spec:
  replicas: 2
  selector:
    matchLabels:
      app: observatory
  template:
    metadata:
      labels:
        app: observatory
    spec:
      imagePullSecrets:
        - name: gitlab-registry-credentials
      containers:
        - name: observatory
          image: IMAGE_NAME_PLACEHOLDER
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: prod
            - name: ALERTING_PROVIDER_API_KEY
              valueFrom:
                secretKeyRef:
                  name: observatory-secrets
                  key: alerting-provider-api-key
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
```

O `Deployment` mantém os pods rodando, mas não cria um endereço para o tráfego. Acrescente um `Service` ao mesmo arquivo:

```yaml
---
apiVersion: v1
kind: Service
metadata:
  name: observatory
spec:
  selector:
    app: observatory
  ports:
    - port: 80
      targetPort: 8080
```

### Deploy manual no DOKS

O DigitalOcean Kubernetes (DOKS) fornece um cluster gerenciado. O mesmo manifesto funciona em GKE, EKS ou AKS; o exemplo usa DOKS para deixar o destino concreto.

Depois de criar um cluster chamado `observatory` no painel da DigitalOcean, autentique o `doctl` com um token da DigitalOcean e instale o kubeconfig local:

```bash
doctl auth init
doctl kubernetes cluster kubeconfig save observatory
kubectl get nodes
```

Antes do deploy, registre no cluster uma credencial de leitura do GitLab Container Registry. Crie um deploy token no projeto com a permissão `read_registry` e defina seus valores no ambiente local:

```bash
export GITLAB_REGISTRY_USER=project-deploy-token-user
export GITLAB_REGISTRY_TOKEN=project-deploy-token
kubectl create secret docker-registry gitlab-registry-credentials \
  --docker-server=registry.gitlab.com \
  --docker-username="$GITLAB_REGISTRY_USER" \
  --docker-password="$GITLAB_REGISTRY_TOKEN" \
  --dry-run=client -o yaml | kubectl apply -f -
```

Crie também o Secret consumido pela aplicação. Defina a variável antes do comando para não deixar a chave escrita no histórico do shell:

```bash
export ALERTING_PROVIDER_API_KEY=dev-only-key
kubectl create secret generic observatory-secrets \
  --from-literal=alerting-provider-api-key="$ALERTING_PROVIDER_API_KEY" \
  --dry-run=client -o yaml | kubectl apply -f -
```

Troque `OWNER` pelo dono do repositório e defina a imagem publicada. Substitua o placeholder antes de aplicar os recursos e acompanhe o rollout:

```bash
export IMAGE_NAME=registry.gitlab.com/GROUP/PROJECT/observatory:latest
sed "s|IMAGE_NAME_PLACEHOLDER|$IMAGE_NAME|g" k8s/deployment.yaml | kubectl apply -f -
kubectl rollout status deployment/observatory
kubectl get pods -l app=observatory
kubectl get service observatory
kubectl port-forward service/observatory 8080:80
```

Com o port-forward ativo, use o request da aula em `http://localhost:8080/api/status`. No cluster, confirme a operação com `kubectl logs deployment/observatory` e consulte `http://localhost:8080/actuator/health` pelo mesmo port-forward.

## Pipeline de deploy

O pipeline roda três etapas em sequência: os testes bloqueiam a publicação, a imagem recebe o SHA do commit e o cluster recebe exatamente essa imagem. Assim o deploy não depende de `latest`, que pode mudar enquanto você investiga uma versão.

Crie `.gitlab-ci.yml`:

```yaml
stages:
  - test
  - publish
  - deploy

variables:
  DOCKER_HOST: tcp://docker:2376
  DOCKER_TLS_CERTDIR: /certs

test:
  stage: test
  image: eclipse-temurin:25-jdk
  script:
    - chmod +x gradlew
    - ./gradlew test bootJar
  artifacts:
    paths:
      - build/libs/observatory.jar

publish-image:
  stage: publish
  image: docker:27.5.1
  services:
    - name: docker:27.5.1-dind
      alias: docker
  needs:
    - job: test
      artifacts: true
  script:
    - docker login "$CI_REGISTRY" --username "$CI_REGISTRY_USER" --password "$CI_REGISTRY_PASSWORD"
    - docker build --tag "$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA" .
    - docker push "$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA"

deploy:
  stage: deploy
  image: bitnami/kubectl:1.31
  needs:
    - publish-image
  environment:
    name: production
  script:
    - mkdir -p "$HOME/.kube"
    - printf '%s' "$KUBE_CONFIG_DATA" | base64 --decode > "$HOME/.kube/config"
    - kubectl create secret docker-registry gitlab-registry-credentials --docker-server=registry.gitlab.com --docker-username="$GITLAB_REGISTRY_USER" --docker-password="$GITLAB_REGISTRY_TOKEN" --dry-run=client -o yaml | kubectl apply -f -
    - kubectl create secret generic observatory-secrets --from-literal=alerting-provider-api-key="$ALERTING_PROVIDER_API_KEY" --dry-run=client -o yaml | kubectl apply -f -
    - IMAGE_NAME="$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA"
    - sed "s|IMAGE_NAME_PLACEHOLDER|$IMAGE_NAME|g" k8s/deployment.yaml | kubectl apply -f -
    - kubectl rollout status deployment/observatory --timeout=120s
```

O GitLab fornece `CI_REGISTRY`, `CI_REGISTRY_USER`, `CI_REGISTRY_PASSWORD` e `CI_REGISTRY_IMAGE` para a publicação. Crie um deploy token do projeto com `read_registry` para `GITLAB_REGISTRY_USER` e `GITLAB_REGISTRY_TOKEN`; o cluster usa essas duas variáveis para baixar a imagem. `KUBE_CONFIG_DATA` contém o kubeconfig do cluster codificado em Base64. `ALERTING_PROVIDER_API_KEY` abastece o Secret da aplicação. Cadastre essas quatro variáveis como protegidas no ambiente `production`.

O ambiente `production` do GitLab pode exigir aprovação antes da etapa `deploy`. Se o teste falhar, `publish-image` não começa. Se a imagem publicar e o rollout falhar, o pipeline mostra o commit exato que precisa ser investigado.

## Estrutura

```
src/main/java/com/example/observatory/
├── ObservatoryApplication.java
├── config/
│   ├── AlertingProperties.java
│   └── ObservatoryProperties.java
└── web/
    └── ObservatoryStatusController.java
src/main/resources/
├── application.yaml
├── application-prod.yaml
└── requests.http
Dockerfile
k8s/
└── deployment.yaml
.gitlab-ci.yml
```
