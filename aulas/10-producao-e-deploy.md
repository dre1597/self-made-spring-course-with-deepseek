# Aula 10 — Produção e deploy

Objetivo: configurar uma aplicação por ambiente, manter secrets fora do código e empacotar o mesmo serviço como jar, container ou native image.

Domínio: um observatório meteorológico que recebe leituras de estações e expõe o estado do serviço. O projeto é próprio desta aula e não depende dos projetos das aulas anteriores.

A aula acompanha uma mesma aplicação até a produção. Primeiro ela aprende a receber configuração sem recompilar; depois separa credenciais do código; por fim vira uma imagem e é executada pelo Kubernetes. Cada etapa resolve um problema diferente. Um container não substitui configuração por ambiente, e um Deployment não substitui o Actuator: eles se encaixam.

## Base do projeto

O projeto precisa de três capacidades: iniciar como uma aplicação Spring, responder HTTP e expor informações operacionais. As dependências representam essas capacidades:

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-actuator")
```

O `spring-boot-starter` fornece o contexto da aplicação. O `webmvc` permite criar a API do observatório. O `actuator` adiciona endpoints operacionais, como saúde e métricas. A aplicação não precisa conhecer Docker ou Kubernetes para funcionar; essas ferramentas vão consumir os endpoints que ela expõe.

A classe principal é pequena porque o Boot monta o contexto a partir das dependências. `@ConfigurationPropertiesScan` é importante nesta aula: ele manda o Spring procurar classes de configuração tipadas, que aparecerão na seção seguinte.

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

Configuração de ambiente não deve exigir uma nova compilação. O mesmo jar precisa poder rodar localmente, em homologação e em produção recebendo valores diferentes de fora.

O Spring Boot monta a configuração combinando fontes. `application.yaml` fornece os valores padrão; quando o perfil `prod` está ativo, `application-prod.yaml` entra por cima e substitui apenas o que mudar:

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

O fallback `dev` existe para facilitar a execução local. Em produção, o ambiente deve declarar explicitamente `prod`; assim não dependemos de um valor implícito para escolher configurações críticas.

A precedência, da maior pra menor:

1. Argumentos de linha de comando (`--server.port=9090`).
2. Variáveis de ambiente (`SERVER_PORT=9090`).
3. Arquivos `application-{profile}.yaml`.
4. `application.yaml`.

Variáveis de ambiente são úteis quando a plataforma de execução injeta valores. O Spring mapeia nomes relaxados: `APP_OBSERVATORY_STATION_NAME` vira `app.observatory.station-name`. Para uma configuração pequena, também dá para passar um bloco como `SPRING_APPLICATION_JSON` ao iniciar o processo:

```bash
SPRING_APPLICATION_JSON='{"app":{"observatory":{"station-name":"north-platform"}}}' java -jar app.jar
```

Isso é uma forma de entrada, não um formato que precise ser usado sempre. Em um deploy, a plataforma costuma injetar variáveis individualmente. O ponto importante é que o valor chega de fora do jar.

Para consumir configuração dentro da aplicação, prefira binding tipado a espalhar `@Value` pelo código. O record abaixo define o contrato da configuração: prefixo, nomes das propriedades e tipos esperados:

```java
package com.example.observatory.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.observatory")
public record ObservatoryProperties(String stationName, Duration readingInterval) {
}
```

O Spring cria um bean `ObservatoryProperties` com os valores encontrados em `app.observatory`. `Duration` também faz o binding de `60s` e `30s` para um tipo que o código consegue usar sem interpretar texto manualmente.

Para verificar o resultado sem abrir o arquivo de configuração, a aplicação expõe um endpoint simples:

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

Esse endpoint não é uma funcionalidade importante do observatório. Ele é uma ferramenta didática e operacional: permite comprovar qual configuração foi carregada. Com o perfil `dev`, `GET /api/status` retorna `local` e `60s`; com `SPRING_PROFILES_ACTIVE=prod`, retorna `central` e `30s`. O mesmo código atende os dois ambientes; só a configuração muda.

```http
### Consulta a configuração carregada
GET http://localhost:8080/api/status
```

## Secrets

Configuração e secret chegam à aplicação pelo mesmo mecanismo, mas têm riscos diferentes. O nome da estação pode estar em um arquivo versionado; uma chave de API não pode. Se a credencial entrar no código, no `application.yaml` versionado ou na imagem, qualquer pessoa com acesso a esse artefato pode recuperá-la.

O observatório envia alertas para um provedor externo. A aplicação conhece o nome da variável que deve receber, mas não conhece a origem do valor. O `application.yaml` completo fica assim:

```yaml
# src/main/resources/application.yaml
spring:
  application:
    name: observatory
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
app:
  observatory:
    station-name: local
    reading-interval: 60s
  alerting:
    provider-api-key: ${ALERTING_PROVIDER_API_KEY}
```

O bloco `app.alerting` usa um placeholder. O arquivo pode ser versionado porque não contém o valor da chave; na inicialização, o Spring procura `ALERTING_PROVIDER_API_KEY` no ambiente.

O binding tipado também vale para a configuração sensível. O código recebe a chave como dependência sem precisar procurar variável de ambiente ou espalhar `System.getenv` pela aplicação. A classe não deve logar nem devolver esse valor pela API:

```java
package com.example.observatory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.alerting")
public record AlertingProperties(String providerApiKey) {
}
```

Se `ALERTING_PROVIDER_API_KEY` não existir, o placeholder sem valor padrão impede o binding e a aplicação não sobe com uma credencial ausente. Falhar no início é mais seguro do que iniciar aparentemente saudável e descobrir a falta da chave só quando chegar o primeiro alerta.

Em desenvolvimento, defina a variável no ambiente antes de executar:

```bash
export ALERTING_PROVIDER_API_KEY=dev-only-key
./gradlew bootRun
```

No Kubernetes, um recurso `Secret` alimenta essa variável. O `Deployment` associa uma chave do Secret à variável `ALERTING_PROVIDER_API_KEY`; a aplicação não precisa saber que o valor veio do Kubernetes. Um cofre como HashiCorp Vault pode fornecer o valor e permitir rotação sem colocar a chave no repositório. A aplicação continua lendo uma propriedade; só a origem do valor muda fora dela.

## GraalVM native image + AOT

Até aqui, o resultado normal do projeto é um jar executado por uma JVM. A compilação nativa é outro formato de entrega: o AOT (ahead-of-time) prepara o contexto do Spring no build, e o GraalVM compila a aplicação para um executável específico do sistema.

O executável nativo costuma iniciar mais rápido e consumir menos memória, mas tem custo de build maior e exige que classes, reflexão e recursos sejam conhecidos antecipadamente. Não é uma versão automaticamente melhor do jar. Ele costuma ser interessante em serverless e scale-to-zero; um serviço que permanece ligado pode continuar no JVM e aproveitar o JIT aquecido.

Adicione o plugin de native image ao `build.gradle.kts`. A versão deve seguir a compatibilidade definida pelo projeto:

```kotlin
plugins {
    id("org.graalvm.buildtools.native") version "0.11.5"
}
```

Com o plugin aplicado, o Gradle expõe `nativeCompile`. A tarefa executa a preparação AOT e chama o compilador nativo. O resultado não é um jar; é um executável no diretório de saída:

```bash
./gradlew nativeCompile
```

Se o destino for um container, o buildpack consegue criar a imagem nativa sem um Dockerfile escrito manualmente. `BP_NATIVE_IMAGE=true` muda o tipo de artefato usado pelo buildpack:

```bash
BP_NATIVE_IMAGE=true ./gradlew bootBuildImage
```

O modelo **closed world** é a principal restrição: o GraalVM analisa o código a partir do `main` e precisa conhecer classes, métodos e recursos em build time. Reflexão e proxies dinâmicos que o Spring não prevê precisam de hints. O Boot gera muitos hints; integrações fora do suporte automático podem exigir configuração adicional ou o tracing agent do GraalVM.

## Docker

Uma imagem Docker é o artefato que será executado pelo ambiente de produção. O fluxo é: o Gradle produz o jar, uma ferramenta monta uma imagem com esse jar e o Docker executa um container a partir dela.

O `bootJar` empacota a aplicação JVM em um arquivo executável. O `bootBuildImage` usa os buildpacks do Spring para transformar esse jar em uma imagem com camadas separadas para dependências, classes e recursos. As camadas permitem reaproveitar partes que não mudaram entre builds:

```bash
./gradlew bootJar
./gradlew bootBuildImage
```

Quando o buildpack atende ao projeto, não há Dockerfile para manter. Se você precisar controlar a imagem manualmente, fixe o nome do artefato no `build.gradle.kts` para o Dockerfile não depender do nome gerado pelo Gradle:

```kotlin
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("observatory.jar")
}
```

Agora o Dockerfile sabe exatamente qual arquivo copiar:

```dockerfile
FROM eclipse-temurin:25-jre
COPY build/libs/observatory.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`docker build` cria a imagem local. `docker run` cria um container a partir dela e injeta o perfil e o secret no momento da execução. O secret não fica gravado na imagem:

```bash
docker build -t observatory:local .
docker run --rm -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e ALERTING_PROVIDER_API_KEY=local-only-key \
  observatory:local
```

O Dockerfile contém o runtime e o jar, mas não contém a chave. A mesma imagem pode rodar em ambientes diferentes porque cada ambiente injeta seu perfil e seus secrets.

## Kubernetes e health checks

Até aqui, a aplicação sabe responder HTTP e o Actuator sabe verificar a saúde do processo. No Kubernetes, falta declarar o que fazer com essa informação.

O Kubernetes lê arquivos YAML chamados **manifests**. Um manifest descreve recursos que devem existir no cluster. Nesta aula, o recurso principal é um `Deployment`: ele diz qual imagem iniciar, quantas réplicas manter, quais variáveis injetar e como verificar cada pod.

A configuração fica dividida em dois arquivos com responsabilidades diferentes:

- `src/main/resources/application-prod.yaml`: configura o Spring Boot e publica os endpoints de saúde;
- `k8s/deployment.yaml`: configura o Kubernetes e diz quais endpoints ele deve consultar.

### Configuração do Spring Boot

O Actuator precisa expor o endpoint `health` e habilitar os grupos de probes. O `application-prod.yaml` completo fica assim:

```yaml
# src/main/resources/application-prod.yaml
spring:
  config:
    activate:
      on-profile: prod
app:
  observatory:
    station-name: central
    reading-interval: 30s
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

Com essa configuração, o Spring Boot expõe:

```text
GET /actuator/health/liveness
GET /actuator/health/readiness
```

Esses endpoints respondem com saúde HTTP. O primeiro representa a pergunta “a aplicação ainda está viva?”. O segundo representa “a aplicação está pronta para receber tráfego?”. A aplicação fornece as respostas; ela ainda não sabe quem vai consumi-las.

### Manifest do Kubernetes

Agora crie o arquivo `k8s/deployment.yaml`. Este arquivo contém dois recursos: um `Deployment`, que mantém os pods rodando, e um `Service`, que cria um endereço estável para o tráfego. O manifest completo fica assim:

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

O campo `livenessProbe` manda o kubelet chamar `/actuator/health/liveness`. Se essa chamada falhar repetidamente, o Kubernetes reinicia o container. O campo `readinessProbe` chama `/actuator/health/readiness`; se falhar, o pod é retirado do tráfego, mas continua rodando para tentar se recuperar.

O `kubectl` envia esse arquivo para a API do cluster. É isso que “configura as probes”:

```bash
kubectl apply -f k8s/deployment.yaml
```

Depois disso, o Deployment cria os pods e o kubelet executa as verificações declaradas no YAML. O Kubernetes não inventa os caminhos e não lê o `application-prod.yaml`; ele apenas chama os endpoints que configuramos no próprio manifest.

Neste projeto, o Actuator verifica apenas a saúde básica da aplicação. O provedor de alertas não entra no grupo de readiness porque o exemplo não implementa um cliente para ele. Em uma aplicação real, adicione um `HealthIndicator` antes de usar uma dependência externa como critério de readiness.

### Deploy manual no DOKS

Um cluster Kubernetes é o ambiente que recebe os manifests e mantém os pods rodando. O DigitalOcean Kubernetes (DOKS) fornece esse cluster de forma gerenciada. O mesmo manifesto funciona em GKE, EKS ou AKS; o exemplo usa DOKS para deixar o destino concreto.

O `kubectl` é o cliente de linha de comando do Kubernetes. Ele não cria o cluster: ele envia comandos para a API de um cluster que já existe. Para saber para qual cluster enviar os comandos, ele usa um arquivo chamado `kubeconfig`.

Depois de criar um cluster chamado `observatory` no painel da DigitalOcean, autentique o `doctl` com um token da DigitalOcean e instale o kubeconfig local:

```bash
doctl auth init
doctl kubernetes cluster kubeconfig save observatory
kubectl get nodes
```

`kubectl get nodes` é uma verificação simples: se retornar os nós, o cliente está autenticado e apontando para o cluster correto. A partir daí, `kubectl apply -f` pode criar ou atualizar os recursos descritos no manifest.

Antes do Deployment, o cluster precisa conseguir baixar a imagem privada do GitLab Container Registry. Para isso, crie um deploy token no projeto com a permissão `read_registry` e registre suas credenciais em um recurso `Secret` do tipo `docker-registry`. Esse Secret é referenciado no campo `imagePullSecrets` do Deployment:

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

Defina a imagem publicada e substitua o placeholder antes de aplicar os recursos. O primeiro comando envia o Deployment e o Service para o cluster; os comandos seguintes acompanham a atualização e permitem acessar o Service localmente:

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

O deploy manual ajuda a entender os recursos, mas não deve depender de comandos executados na máquina de uma pessoa. A pipeline automatiza o mesmo fluxo em três etapas:

- `test`: executa os testes e produz o jar. Como a aula usa Testcontainers, esse job também precisa de acesso a um daemon Docker;
- `publish`: monta a imagem e publica no GitLab Container Registry;
- `deploy`: autentica no cluster, aplica os Secrets e atualiza o Deployment.

A imagem recebe o SHA do commit, não apenas `latest`. Assim, o cluster executa uma versão imutável que pode ser identificada e investigada depois.

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
  services:
    - name: docker:27.5.1-dind
      alias: docker
  variables:
    DOCKER_HOST: tcp://docker:2375
    DOCKER_TLS_CERTDIR: ""
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

No job `test`, o serviço `docker:dind` fornece um daemon Docker separado para o Testcontainers iniciar os containers usados pelos testes. `DOCKER_HOST` aponta o cliente do Testcontainers para esse daemon; `DOCKER_TLS_CERTDIR` vazio deixa essa comunicação sem TLS dentro da rede temporária do job. O runner do GitLab precisa permitir serviços Docker-in-Docker.

O job `publish-image` usa o jar produzido pelo job anterior e publica a imagem no registry. O job `deploy` usa o kubeconfig e os secrets armazenados nas variáveis protegidas do GitLab para executar os mesmos comandos que usamos manualmente.

As variáveis no nível global usam a configuração TLS esperada pelo job que publica a imagem. O job `test` sobrescreve essas duas variáveis porque seu daemon Docker roda com TLS desativado dentro do job. Essa diferença existe por causa do modo como cada job conversa com o Docker; ela não muda a aplicação nem o container que será publicado.

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
