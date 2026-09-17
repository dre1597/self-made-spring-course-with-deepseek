# Aula 13 — gRPC

Objetivo: definir um contrato protobuf, expor um serviço gRPC com Spring gRPC, consumir o stub gerado e testar uma chamada unária e um streaming.

Domínio: telemetria de bicicletas compartilhadas. O serviço de frota consulta uma bicicleta pelo id e transmite suas posições; o serviço de operações consome essas informações. Os três projetos desta aula são separados dos projetos anteriores.

## Base dos projetos

Esta aula usa três projetos próprios:

- `fleet-contract`: biblioteca com o contrato protobuf e as classes geradas;
- `fleet-server`: processo gRPC que atende na porta `9090`;
- `operations-client`: processo HTTP MVC na porta `8080` que chama o servidor gRPC.

O servidor e o cliente não compartilham classes de implementação. Ambos dependem do artefato `com.example:fleet-contract:1.0.0`, publicado pelo projeto de contrato.

Os três usam Java 25 e Gradle com Kotlin DSL. Cada um fica no seu próprio diretório.

### Projeto do contrato

O `fleet-contract` não é uma aplicação Spring Boot. É uma biblioteca Java que compila o `.proto` e publica um jar com as classes geradas. Por isso o build dele não aplica o plugin do Boot.

No IntelliJ, crie um projeto **Gradle** com **Kotlin** como DSL da build e nome `fleet-contract`. Se o wizard já gerou `settings.gradle.kts`, confirme que o conteúdo é este:

```kotlin
rootProject.name = "fleet-contract"
```

Substitua todo o conteúdo de `build.gradle.kts` por este arquivo completo. O plugin `com.google.protobuf` gera as mensagens, o stub bloqueante e o stub assíncrono durante o build; o `maven-publish` publica o jar no repositório Maven local:

```kotlin
plugins {
    `java-library`
    `maven-publish`
    id("com.google.protobuf") version "0.9.6"
}

group = "com.example"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api("com.google.protobuf:protobuf-java:4.35.1")
    api("io.grpc:grpc-protobuf:1.83.1")
    api("io.grpc:grpc-stub:1.83.1")

    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.35.1"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.83.1"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                create("grpc")
            }
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}
```

As versões `4.35.1` do protobuf e `1.83.1` do gRPC são as que o Spring Boot 4.1 gerencia. Usar as mesmas versões aqui evita mistura de runtime entre o contrato e as aplicações.

O `java-library` traz a configuração `api`, necessária para que quem consome o jar receba também as dependências do protobuf e do gRPC. O `maven-publish` traz o bloco `publishing`.

O `org.apache.tomcat:annotations-api` em `compileOnly` existe por um detalhe do gerador do gRPC: o código gerado carrega `@javax.annotation.Generated`, que saiu do JDK a partir do Java 11. A anotação tem retenção de código-fonte, então a dependência só é necessária na compilação e não vai para o jar publicado.

Depois de editar o build, rode o sync do Gradle no IntelliJ (o ícone de elefante, "Load Gradle Changes"). Sem o sync a IDE mostra os plugins em vermelho mesmo com o arquivo correto.

Com o sync ok, publique o contrato dentro do diretório `fleet-contract`:

```bash
./gradlew publishToMavenLocal
```

O `publishToMavenLocal` grava o jar em `~/.m2/repository`, que é de onde o servidor e o cliente o consomem.

### Projeto do servidor

O `fleet-server` é um projeto Spring Boot próprio, no pacote `com.example.fleetserver`, com a aplicação na porta gRPC `9090`. O projeto-base do IntelliJ já traz `repositories { mavenCentral() }`; acrescente `mavenLocal()` para o Gradle achar o contrato publicado:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
```

As dependências específicas entram no projeto-base:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-grpc-server")
    implementation("com.example:fleet-contract:1.0.0")
    implementation("io.grpc:grpc-services")
}
```

O `spring-boot-starter-grpc-server` sobe o servidor Netty e expõe todo bean `BindableService`. O `io.grpc:grpc-services` habilita o reflection, que a IDE usa para ler o contrato sem ter o `.proto` local. A versão do `grpc-services` vem gerenciada pelo Boot, por isso não aparece no build.

### Projeto do cliente

O `operations-client` é um projeto Spring Boot próprio, no pacote `com.example.operationsclient`, com a API HTTP na porta `8080`. O projeto-base do IntelliJ já traz `repositories { mavenCentral() }`; acrescente `mavenLocal()` também aqui:

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}
```

As dependências específicas entram no projeto-base:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-grpc-client")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("com.example:fleet-contract:1.0.0")
}
```

O starter do cliente fornece o `GrpcChannelFactory`. O `webmvc` expõe as rotas HTTP que disparam as chamadas gRPC. O cliente não gera stub: ele usa as classes que vieram dentro do jar do contrato.

## Por que gRPC

REST é JSON por HTTP, contrato solto. gRPC é contrato tipado em protobuf, com serialização binária e streaming nativo. Compensa em comunicação interna de alto volume, onde o overhead do JSON e o contrato implícito doem. Serviço a serviço, com protobuf, o cliente ganha um stub gerado: erro de tipo vira erro de compilação, não de runtime.

O `.proto` define o contrato e gera as classes Java; `@GrpcService` expõe o serviço; o cliente injeta um stub. O contrato é a fronteira compartilhada: servidor e cliente compilam contra a mesma definição, mas não compartilham implementação.

## O contrato

Mantenha este arquivo em `fleet-contract/src/main/proto/fleet.proto`:

```protobuf
syntax = "proto3";

package fleet;

option java_package = "com.example.fleetcontract.api";
option java_multiple_files = true;

service FleetTelemetry {
  rpc GetVehicle(GetVehicleRequest) returns (Vehicle);
  rpc StreamPositions(StreamPositionsRequest) returns (stream Position);
}

message GetVehicleRequest {
  string vehicle_id = 1;
}

message StreamPositionsRequest {
  string vehicle_id = 1;
}

message Vehicle {
  string vehicle_id = 1;
  string status = 2;
  int32 battery_percentage = 3;
}

message Position {
  string vehicle_id = 1;
  double latitude = 2;
  double longitude = 3;
  int64 recorded_at_epoch_seconds = 4;
}
```

O `service` vira a classe base `FleetTelemetryGrpc.FleetTelemetryImplBase` e os stubs. `returns (stream Position)` é server-streaming: o servidor envia posições uma por uma e fecha o fluxo quando termina. Os números dos campos fazem parte do contrato; não reutilize um número antigo para outro significado.

## Servidor

O `@GrpcService` registra o bean como serviço gRPC. Estenda a base gerada e implemente os métodos. O servidor devolve `NOT_FOUND` para uma bicicleta desconhecida, em vez de enviar uma mensagem vazia:

```java
package com.example.fleetserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FleetApplication {

    public static void main(String[] args) {
        SpringApplication.run(FleetApplication.class, args);
    }
}
```

O servidor usa a porta gRPC `9090`:

```yaml
# fleet-server/src/main/resources/application.yaml
spring:
  grpc:
    server:
      port: 9090
```

```java
package com.example.fleetserver.grpc;

import io.grpc.stub.StreamObserver;
import io.grpc.Status;

import com.example.fleetcontract.api.FleetTelemetryGrpc;
import com.example.fleetcontract.api.GetVehicleRequest;
import com.example.fleetcontract.api.Position;
import com.example.fleetcontract.api.StreamPositionsRequest;
import com.example.fleetcontract.api.Vehicle;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class FleetTelemetryGrpcService extends FleetTelemetryGrpc.FleetTelemetryImplBase {

    @Override
    public void getVehicle(GetVehicleRequest request, StreamObserver<Vehicle> responseObserver) {
        if (!request.getVehicleId().equals("bike-001")) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Bicicleta não encontrada")
                    .asRuntimeException());
            return;
        }
        responseObserver.onNext(Vehicle.newBuilder()
                .setVehicleId("bike-001")
                .setStatus("AVAILABLE")
                .setBatteryPercentage(87)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void streamPositions(StreamPositionsRequest request, StreamObserver<Position> responseObserver) {
        for (int index = 0; index < 3; index++) {
            responseObserver.onNext(Position.newBuilder()
                    .setVehicleId(request.getVehicleId())
                    .setLatitude(-22.90 + index * 0.001)
                    .setLongitude(-43.17 + index * 0.001)
                    .setRecordedAtEpochSeconds(System.currentTimeMillis() / 1000)
                    .build());
        }
        responseObserver.onCompleted();
    }
}
```

Unário (`getVehicle`) manda uma resposta e fecha com `onCompleted`. Streaming (`streamPositions`) manda várias `onNext` antes de fechar. `onError` termina a chamada com um status gRPC que o cliente consegue tratar. O servidor gRPC sobe numa porta própria, aqui configurada em `9090`, separada do HTTP.

## Cliente

O build do `operations-client` já está definido acima. Mantenha este arquivo completo em `operations-client/src/main/resources/application.yaml`:

```yaml
# operations-client/src/main/resources/application.yaml
server:
  port: 8080
```

```java
package com.example.operationsclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OperationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OperationsApplication.class, args);
    }
}
```

O `GrpcChannelFactory` cria um channel para o endereço direto `localhost:9090`, e o stub bloqueante nasce desse channel:

```java
package com.example.operationsclient.grpc;

import com.example.fleetcontract.api.FleetTelemetryGrpc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class FleetGrpcClientConfiguration {

    @Bean
    FleetTelemetryGrpc.FleetTelemetryBlockingStub fleetTelemetryStub(GrpcChannelFactory channels) {
        return FleetTelemetryGrpc.newBlockingStub(channels.createChannel("localhost:9090"));
    }
}
```

O stub entra no serviço de operações. A chamada parece método local, mas continua sujeita a timeout, status gRPC e falha de rede:

```java
package com.example.operationsclient.fleet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.example.fleetcontract.api.FleetTelemetryGrpc;
import com.example.fleetcontract.api.GetVehicleRequest;
import com.example.fleetcontract.api.Position;
import com.example.fleetcontract.api.StreamPositionsRequest;
import com.example.fleetcontract.api.Vehicle;

import org.springframework.stereotype.Service;

@Service
public class FleetOperationsService {

    private final FleetTelemetryGrpc.FleetTelemetryBlockingStub fleetTelemetry;

    public FleetOperationsService(FleetTelemetryGrpc.FleetTelemetryBlockingStub fleetTelemetry) {
        this.fleetTelemetry = fleetTelemetry;
    }

    public Vehicle findVehicle(String vehicleId) {
        return fleetTelemetry.getVehicle(GetVehicleRequest.newBuilder()
                .setVehicleId(vehicleId)
                .build());
    }

    public List<Position> streamPositions(String vehicleId) {
        Iterator<Position> positions = fleetTelemetry.streamPositions(
                StreamPositionsRequest.newBuilder().setVehicleId(vehicleId).build());
        List<Position> result = new ArrayList<>();
        positions.forEachRemaining(result::add);
        return result;
    }
}
```

As rotas HTTP devolvem JSON. As classes geradas pelo protobuf não serializam bem como JSON, então o cliente converte as mensagens para records antes de responder. O contrato gRPC fica restrito à camada que fala com o servidor, e a API HTTP tem o próprio formato.

`operations-client/src/main/java/com/example/operationsclient/web/VehicleResponse.java`:

```java
package com.example.operationsclient.web;

public record VehicleResponse(String vehicleId, String status, int batteryPercentage) {
}
```

`operations-client/src/main/java/com/example/operationsclient/web/PositionResponse.java`:

```java
package com.example.operationsclient.web;

public record PositionResponse(
        String vehicleId,
        double latitude,
        double longitude,
        long recordedAtEpochSeconds) {
}
```

Exponha duas rotas HTTP para testar o cliente:

```java
package com.example.operationsclient.web;

import java.util.List;

import com.example.fleetcontract.api.Position;
import com.example.fleetcontract.api.Vehicle;
import com.example.operationsclient.fleet.FleetOperationsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fleet")
public class FleetOperationsController {

    private final FleetOperationsService fleetOperations;

    public FleetOperationsController(FleetOperationsService fleetOperations) {
        this.fleetOperations = fleetOperations;
    }

    @GetMapping("/{vehicleId}")
    public VehicleResponse vehicle(@PathVariable String vehicleId) {
        Vehicle vehicle = fleetOperations.findVehicle(vehicleId);
        return new VehicleResponse(
                vehicle.getVehicleId(),
                vehicle.getStatus(),
                vehicle.getBatteryPercentage());
    }

    @GetMapping("/{vehicleId}/positions")
    public List<PositionResponse> positions(@PathVariable String vehicleId) {
        return fleetOperations.streamPositions(vehicleId).stream()
                .map(FleetOperationsController::toResponse)
                .toList();
    }

    private static PositionResponse toResponse(Position position) {
        return new PositionResponse(
                position.getVehicleId(),
                position.getLatitude(),
                position.getLongitude(),
                position.getRecordedAtEpochSeconds());
    }
}
```

O endereço vem direto no código para manter a primeira execução explícita. Em produção, configure channels nomeados por propriedade e use interceptores para autenticação, timeout, tracing e retry. O stub bloqueante é adequado para o endpoint MVC; uma aplicação WebFlux usaria o stub assíncrono ou reativo, sem chamar `block()` no caminho da requisição.

## Testando o contrato

O IntelliJ executa chamadas gRPC direto do HTTP Client. O request começa com a palavra `GRPC` e a IDE trata como gRPC. Para isso, os plugins **Protocol Buffers** e **gRPC** precisam estar habilitados.

Rode o `FleetApplication` e o `OperationsApplication` pela IDE. Com os dois processos de pé, use os arquivos `requests.http` de cada projeto.

No `fleet-server`, as chamadas vão direto ao servidor gRPC:

```http
# fleet-server/src/main/resources/requests.http

### Chamada unária
GRPC localhost:9090/fleet.FleetTelemetry/GetVehicle

{
  "vehicleId": "bike-001"
}

### Streaming server-side
GRPC localhost:9090/fleet.FleetTelemetry/StreamPositions

{
  "vehicleId": "bike-001"
}

### Veículo inexistente
GRPC localhost:9090/fleet.FleetTelemetry/GetVehicle

{
  "vehicleId": "bike-404"
}
```

O servidor habilita reflection (o `io.grpc:grpc-services` já está no build dele), então a IDE completa os serviços e os campos pelo contrato publicado, sem precisar de uma cópia local do `.proto`.

A chamada unária responde um `Vehicle` com `vehicleId` igual a `bike-001`, status `AVAILABLE` e bateria `87`. O streaming envia três mensagens `Position` e encerra o stream com `onCompleted`. O veículo inexistente retorna `NotFound`.

No `operations-client`, os requests atravessam o cliente Java e o stub gerado:

```http
# operations-client/src/main/resources/requests.http

### Veículo pelo cliente de operações
GET http://localhost:8080/api/fleet/bike-001

### Posições pelo cliente de operações
GET http://localhost:8080/api/fleet/bike-001/positions

### Veículo inexistente pelo cliente
GET http://localhost:8080/api/fleet/bike-404
```

O primeiro request devolve o veículo em JSON. O segundo devolve uma lista JSON com as três posições. Se o cliente de operações ainda não estiver rodando, os requests HTTP falham mesmo que o servidor gRPC esteja disponível. Para `bike-404`, o cliente não trata a exceção, então o Spring responde `500`; uma API de produção deve traduzir o status `NOT_FOUND` para `404`.

Como alternativa fora da IDE, o `grpcurl` faz as mesmas chamadas no terminal. Rode do diretório que contém os três projetos (a pasta pai), porque os caminhos do `.proto` são relativos a ela:

```bash
grpcurl -plaintext \
  -import-path fleet-contract/src/main/proto \
  -proto fleet.proto \
  -d '{"vehicleId":"bike-001"}' \
  localhost:9090 fleet.FleetTelemetry/GetVehicle

grpcurl -plaintext \
  -import-path fleet-contract/src/main/proto \
  -proto fleet.proto \
  -d '{"vehicleId":"bike-001"}' \
  localhost:9090 fleet.FleetTelemetry/StreamPositions

grpcurl -plaintext \
  -import-path fleet-contract/src/main/proto \
  -proto fleet.proto \
  -d '{"vehicleId":"bike-404"}' \
  localhost:9090 fleet.FleetTelemetry/GetVehicle
```

## gRPC vs REST

gRPC vence onde contrato forte, baixa sobrecarga e streaming importam, e onde o custo de manter o `.proto` compensa. REST vence em simplicidade, cache HTTP, integração com navegador e APIs públicas. O ponto comum: ambos são chamadas entre processos; a escolha depende dos consumidores e da forma do tráfego.

O cliente gRPC deste projeto usa stub bloqueante porque o processo de operações usa MVC. Em um cliente WebFlux, use o stub assíncrono ou reativo e preserve o fluxo sem bloquear a thread. Para testes automatizados, Spring gRPC oferece transporte in-process com `@AutoConfigureTestGrpcTransport`, evitando abrir a porta `9090`; o teste ainda deve verificar resposta unária, streaming e status `NOT_FOUND`.

## Estrutura

```
fleet-contract/
├── build.gradle.kts
├── settings.gradle.kts
└── src/main/proto/fleet.proto
fleet-server/
└── src/
    ├── main/java/com/example/fleetserver/
    │   ├── FleetApplication.java
    │   └── grpc/FleetTelemetryGrpcService.java
    └── main/resources/
        ├── application.yaml
        └── requests.http
operations-client/
└── src/
    ├── main/java/com/example/operationsclient/
    │   ├── OperationsApplication.java
    │   ├── grpc/FleetGrpcClientConfiguration.java
    │   ├── fleet/FleetOperationsService.java
    │   └── web/
    │       ├── FleetOperationsController.java
    │       ├── VehicleResponse.java
    │       └── PositionResponse.java
    └── main/resources/
        ├── application.yaml
        └── requests.http
```
