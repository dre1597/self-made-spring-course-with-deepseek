# Aula 13 — gRPC

Objetivo: definir um contrato protobuf, expor um serviço gRPC com Spring gRPC, consumir o stub gerado e testar uma chamada unária e um streaming.

Domínio: telemetria de bicicletas compartilhadas. O serviço de frota consulta uma bicicleta pelo id e transmite suas posições; o serviço de operações consome essas informações. São dois projetos próprios desta aula, separados dos projetos anteriores.

## Base dos projetos

O contrato protobuf fica num módulo compartilhado pelos dois projetos. O servidor e o cliente dependem do artefato gerado por esse módulo. O plugin gera mensagens, stubs bloqueantes e stubs assíncronos durante o build:

```kotlin
plugins {
    id("com.google.protobuf") version "0.9.5"
}

dependencies {
    implementation("com.google.protobuf:protobuf-java")
    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-stub")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.31.1"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.75.0"
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
```

O servidor acrescenta `spring-boot-starter-grpc-server`; o cliente acrescenta `spring-boot-starter-grpc-client`. O projeto de operações também usa `spring-boot-starter-webmvc` para expor um endpoint HTTP que dispara a chamada gRPC.

## Por que gRPC

REST é JSON por HTTP, contrato solto. gRPC é contrato tipado em protobuf, com serialização binária e streaming nativo. Compensa em comunicação interna de alto volume, onde o overhead do JSON e o contrato implícito doem. Serviço a serviço, com protobuf, o cliente ganha um stub gerado: erro de tipo vira erro de compilação, não de runtime.

Spring gRPC 1.0.x roda com Boot 4.1. O `.proto` define o contrato e gera as classes Java; `@GrpcService` expõe o serviço; o cliente injeta um stub. O contrato é a fronteira compartilhada: servidor e cliente compilam contra a mesma definição, mas não compartilham implementação.

## O contrato

`src/main/proto/fleet.proto`:

```protobuf
syntax = "proto3";

package fleet;

option java_package = "com.example.fleet.api";
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
package com.example.fleet;

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
spring:
  grpc:
    server:
      port: 9090
```

```java
package com.example.fleet.grpc;

import io.grpc.stub.StreamObserver;
import io.grpc.Status;

import com.example.fleet.api.FleetTelemetryGrpc;
import com.example.fleet.api.GetVehicleRequest;
import com.example.fleet.api.Position;
import com.example.fleet.api.StreamPositionsRequest;
import com.example.fleet.api.Vehicle;
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

O projeto de operações usa estes starters:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-grpc-client")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
```

```java
package com.example.operations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class OperationsApplication {

    public static void main(String[] args) {
        SpringApplication.run(OperationsApplication.class, args);
    }
}
```

O `GrpcChannelFactory` cria o channel e o stub bloqueante nasce dele:

```java
package com.example.operations.grpc;

import com.example.fleet.api.FleetTelemetryGrpc;

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
package com.example.operations.fleet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.example.fleet.api.FleetTelemetryGrpc;
import com.example.fleet.api.GetVehicleRequest;
import com.example.fleet.api.Position;
import com.example.fleet.api.StreamPositionsRequest;
import com.example.fleet.api.Vehicle;

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

Exponha duas rotas HTTP apenas para conseguir testar o cliente com `curl`:

```java
package com.example.operations.web;

import java.util.List;

import com.example.fleet.api.Position;
import com.example.fleet.api.Vehicle;
import com.example.operations.fleet.FleetOperationsService;
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
    public Vehicle vehicle(@PathVariable String vehicleId) {
        return fleetOperations.findVehicle(vehicleId);
    }

    @GetMapping("/{vehicleId}/positions")
    public List<Position> positions(@PathVariable String vehicleId) {
        return fleetOperations.streamPositions(vehicleId);
    }
}
```

O address vem inline no exemplo para manter a primeira execução explícita. Em produção, configure channels nomeados por propriedade e use interceptores para autenticação, timeout, tracing e retry. O stub bloqueante é adequado para o endpoint MVC; uma aplicação WebFlux usaria o stub assíncrono ou reativo, sem chamar `block()` no caminho da requisição.

## Testando o contrato

Suba primeiro o `FleetApplication` na porta `9090` e depois o `OperationsApplication` na porta HTTP padrão `8080`. O `grpcurl` chama o servidor diretamente, enquanto o `curl` atravessa o cliente Java e o stub gerado.

Chamada unária:

```bash
grpcurl -plaintext \
  -import-path src/main/proto \
  -proto fleet.proto \
  -d '{"vehicleId":"bike-001"}' \
  localhost:9090 fleet.FleetTelemetry/GetVehicle
```

Streaming server-side:

```bash
grpcurl -plaintext \
  -import-path src/main/proto \
  -proto fleet.proto \
  -d '{"vehicleId":"bike-001"}' \
  localhost:9090 fleet.FleetTelemetry/StreamPositions
```

Pelo cliente de operações:

```bash
curl http://localhost:8080/api/fleet/bike-001
curl http://localhost:8080/api/fleet/bike-001/positions
```

Use `bike-404` para observar o status `NOT_FOUND` no `grpcurl`. A chamada HTTP do cliente precisa traduzir esse status para o erro HTTP adequado em uma API de produção; o exemplo mantém a exceção gRPC visível para mostrar a fronteira entre os protocolos.

## gRPC vs REST

gRPC vence onde contrato forte, baixa sobrecarga e streaming importam, e onde o custo de manter o `.proto` compensa. REST vence em simplicidade, cache HTTP, integração com navegador e APIs públicas. O ponto comum: ambos são chamadas entre processos; a escolha depende dos consumidores e da forma do tráfego.

O cliente gRPC deste projeto usa stub bloqueante porque o processo de operações usa MVC. Em um cliente WebFlux, use o stub assíncrono ou reativo e preserve o fluxo sem bloquear a thread. Para testes automatizados, Spring gRPC oferece transporte in-process com `@AutoConfigureTestGrpcTransport`, evitando abrir a porta `9090`; o teste ainda deve verificar resposta unária, streaming e status `NOT_FOUND`.

## Estrutura

```
fleet-contract/
└── src/main/proto/fleet.proto
fleet-server/
└── src/main/java/com/example/fleet/
    ├── FleetApplication.java
    └── grpc/
        └── FleetTelemetryGrpcService.java
operations-client/
└── src/main/java/com/example/operations/
    ├── OperationsApplication.java
    ├── grpc/
    │   └── FleetGrpcClientConfiguration.java
    ├── fleet/
    │   └── FleetOperationsService.java
    └── web/
        └── FleetOperationsController.java
```
