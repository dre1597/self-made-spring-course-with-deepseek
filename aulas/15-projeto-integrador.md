# Aula 15 — Projeto integrador

Objetivo: montar uma plataforma imobiliária de dois processos e atravessar os tópicos do curso no ponto do fluxo em que cada um faz sentido: REST, JPA, Security e Modulith no anúncio e na visita; RabbitMQ na integração assíncrona; gRPC na abertura de ordem de manutenção; Spring AI no assistente; Spring Batch no relatório mensal; observabilidade e deploy na operação.

Domínio: a imobiliária tem anúncios de imóveis, visitas agendadas por clientes, pedidos de manutenção e um relatório mensal de visitas. O processo `property-platform` é dono dos anúncios, das visitas e dos relatórios, e fala HTTP na porta `8080`. O processo `maintenance-service` é dono das ordens de serviço, atende gRPC na porta `9090` e expõe a gestão HTTP na porta `8081`.

Cada trecho aponta de qual aula o tópico veio. A aula tem sete partes, feitas na ordem: cada parte fecha com o projeto compilando e um teste pra rodar. Nada de dependência ou configuração aparece antes da parte que usa. As dependências são citadas para adicionar ao build; o `application.yaml` é mostrado inteiro a cada mudança.

## Base dos projetos

A aula usa três diretórios:

- `property-platform`: processo HTTP MVC na porta `8080`, pacote `com.example.propertyplatform`. É o monólito modular do produto.
- `maintenance-service`: processo gRPC na porta `9090` e gestão HTTP na `8081`, pacote `com.example.maintenanceservice`. É o serviço de ordens de serviço.
- `maintenance-contract`: biblioteca com o contrato protobuf e as classes geradas, pacote `com.example.maintenancecontract.api`. Não é um processo; vira um jar no Maven local.

Os dois processos trocam mensagem por RabbitMQ e conversam por gRPC. O `property-platform` é um monólito modular com os módulos de negócio `listing`, `visit`, `maintenance`, `assistant`, `reporting` e `notification`, mais `config`, `web` e `security` de apoio.

O `property-platform` é criado agora, na Parte 1. O `maintenance-contract` e o `maintenance-service` são criados na Parte 3, quando o gRPC entra. Os três são projetos Gradle com Kotlin como DSL, Java 25 e Spring Boot 4.1.1 nos dois processos.

## Parte 1 — Anúncio: REST, JPA, Security e Modulith

O anúncio é o primeiro fluxo. O corretor cria um imóvel, o cliente lista os disponíveis. Aqui entram o monólito modular (aula 11), a persistência JPA (aula 04), a API REST com validação e `ProblemDetail` (aula 03) e a autenticação JWT com autorização por role (aula 05).

### Dependências

No IntelliJ, crie um projeto **Gradle** com **Kotlin** e nome `property-platform`. Acrescente ao build:

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
implementation("org.springframework.boot:spring-boot-starter-security")
implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
implementation("org.springframework.boot:spring-boot-starter-validation")
implementation(platform("org.springframework.modulith:spring-modulith-bom:2.1.1"))
implementation("org.springframework.modulith:spring-modulith-starter-jpa")
runtimeOnly("com.h2database:h2")
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.springframework.modulith:spring-modulith-starter-test")
```

O `webmvc` sustenta a API, `data-jpa` a persistência, `security` e `oauth2-resource-server` o JWT (aula 05), `validation` os `@NotBlank`/`@Positive`/`@Valid` (aula 03). O BOM `spring-modulith-bom:2.1.1` alinha as versões do Modulith, que o BOM do Boot não gerencia (aula 11); o `spring-modulith-starter-jpa` liga a checagem dos módulos e o registro de eventos, e o `spring-modulith-starter-test` traz o teste arquitetural. O `h2` é o banco da aula.

### Configuração

Mantenha este `application.yaml` completo em `property-platform/src/main/resources/application.yaml`:

```yaml
server:
  port: 8080
spring:
  application:
    name: property-platform
  datasource:
    url: jdbc:h2:mem:property;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
  modulith:
    events:
      republish-outstanding-events-on-restart: true
app:
  security:
    jwt-secret: ${JWT_SECRET:dev-secret-com-pelo-menos-32-caracteres}
    users:
      - username: agent
        password: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
        roles:
          - AGENT
      - username: client
        password: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
        roles:
          - CLIENT
```

Os hashes de `agent` e `client` são o BCrypt de `password`, o mesmo da aula 05. O `jwt-secret` tem fallback de dev e vem de `JWT_SECRET` em produção. O `ddl-auto: update` deixa o Hibernate criar o schema nesta aula; em produção, o schema fica com Flyway e `ddl-auto: validate` (aula 04). O `republish-outstanding-events-on-restart` é usado na Parte 2.

### O monólito modular

O `property-platform` é um monólito modular: um processo, um deploy, mas separado em módulos com fronteira verificável. O `@Modulithic` mantém o comportamento do Boot e liga a checagem dos módulos. Cada subpacote direto do pacote raiz vira um módulo.

```java
package com.example.propertyplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic
@SpringBootApplication
public class PropertyPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropertyPlatformApplication.class, args);
    }
}
```

Os módulos de negócio desta aula: `listing` (anúncio), `visit` (visita), `maintenance` (manutenção), `assistant` (assistente), `reporting` (relatório) e `notification` (confirmação). Cada subpacote direto também vira módulo; `config`, `web` e `security` são de apoio.

O módulo `listing` expõe uma API mínima: a interface `PropertyCatalog` e o record `PropertySummary`. Os outros módulos dependem desses tipos, nunca das entidades ou do repositório. O `@ApplicationModuleListener` entra na Parte 2.

O teste arquitetural roda `ApplicationModules.of(...).verify()` e falha quando um módulo fura o limite de outro (aula 11):

```java
package com.example.propertyplatform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTest {

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(PropertyPlatformApplication.class).verify();
    }
}
```

### Persistência

A entidade do imóvel. O nome da tabela vai explícito no plural; a auditoria usa `@CreatedDate` (aula 04):

```java
package com.example.propertyplatform.listing;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "properties")
@EntityListeners(AuditingEntityListener.class)
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private BigDecimal monthlyRent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Property() {
    }

    public Property(String title, String city, BigDecimal monthlyRent) {
        this.title = title;
        this.city = city;
        this.monthlyRent = monthlyRent;
        this.status = ListingStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCity() {
        return city;
    }

    public BigDecimal getMonthlyRent() {
        return monthlyRent;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
```

O status do anúncio:

```java
package com.example.propertyplatform.listing;

public enum ListingStatus {

    ACTIVE,
    RENTED
}
```

O `@EnableJpaAuditing` liga o preenchimento de `@CreatedDate`:

```java
package com.example.propertyplatform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfiguration {
}
```

O repositório cobre o cadastro e a busca por cidade e teto de aluguel:

```java
package com.example.propertyplatform.listing;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    List<Property> findByStatusAndMonthlyRentLessThanEqual(ListingStatus status, BigDecimal monthlyRent);

    List<Property> findByCityAndStatusAndMonthlyRentLessThanEqual(
            String city, ListingStatus status, BigDecimal monthlyRent);
}
```

### A API do módulo listing

A interface pública do módulo e o resumo que os outros módulos enxergam:

```java
package com.example.propertyplatform.listing;

import java.math.BigDecimal;
import java.util.List;

public interface PropertyCatalog {

    PropertySummary getById(Long id);

    List<PropertySummary> findAvailable(String city, BigDecimal maxMonthlyRent);
}
```

```java
package com.example.propertyplatform.listing;

import java.math.BigDecimal;

public record PropertySummary(Long id, String title, String city, BigDecimal monthlyRent) {
}
```

O serviço implementa a API. O `@Transactional` delimita a escrita e a consulta:

```java
package com.example.propertyplatform.listing;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyService implements PropertyCatalog {

    private final PropertyRepository repository;

    public PropertyService(PropertyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Property create(String title, String city, BigDecimal monthlyRent) {
        return repository.save(new Property(title, city, monthlyRent));
    }

    @Transactional(readOnly = true)
    public List<Property> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public PropertySummary getById(Long id) {
        Property property = repository.findById(id)
                .orElseThrow(() -> new PropertyNotFoundException(id));
        return toSummary(property);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropertySummary> findAvailable(String city, BigDecimal maxMonthlyRent) {
        List<Property> properties = (city == null || city.isBlank())
                ? repository.findByStatusAndMonthlyRentLessThanEqual(ListingStatus.ACTIVE, maxMonthlyRent)
                : repository.findByCityAndStatusAndMonthlyRentLessThanEqual(
                        city, ListingStatus.ACTIVE, maxMonthlyRent);
        return properties.stream()
                .map(PropertyService::toSummary)
                .toList();
    }

    private static PropertySummary toSummary(Property property) {
        return new PropertySummary(
                property.getId(), property.getTitle(), property.getCity(), property.getMonthlyRent());
    }
}
```

Os records de entrada e saída da rota, com Bean Validation (aula 03):

```java
package com.example.propertyplatform.listing;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreatePropertyRequest(
        @NotBlank String title,
        @NotBlank String city,
        @Positive BigDecimal monthlyRent) {
}
```

```java
package com.example.propertyplatform.listing;

import java.math.BigDecimal;

public record PropertyResponse(Long id, String title, String city, BigDecimal monthlyRent, String status) {
}
```

A exceção do domínio, no pacote raiz do módulo:

```java
package com.example.propertyplatform.listing;

public class PropertyNotFoundException extends RuntimeException {

    public PropertyNotFoundException(Long id) {
        super("Imóvel não encontrado: " + id);
    }
}
```

O controlador expõe o cadastro e a listagem. O `POST` exige a role `AGENT`; a listagem exige apenas autenticação:

```java
package com.example.propertyplatform.listing;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/properties")
public class PropertyController {

    private final PropertyService propertyService;

    public PropertyController(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('AGENT')")
    public PropertyResponse create(@Valid @RequestBody CreatePropertyRequest request) {
        Property property = propertyService.create(request.title(), request.city(), request.monthlyRent());
        return toResponse(property);
    }

    @GetMapping
    public List<PropertyResponse> findAll() {
        return propertyService.findAll().stream()
                .map(PropertyController::toResponse)
                .toList();
    }

    private static PropertyResponse toResponse(Property property) {
        return new PropertyResponse(
                property.getId(),
                property.getTitle(),
                property.getCity(),
                property.getMonthlyRent(),
                property.getStatus().name());
    }
}
```

O tratamento global de erro devolve `ProblemDetail` (aula 03):

```java
package com.example.propertyplatform.web;

import com.example.propertyplatform.listing.PropertyNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PropertyNotFoundException.class)
    ProblemDetail handleNotFound(PropertyNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Corpo inválido");
        exception.getBindingResult().getFieldErrors().forEach(error ->
                problem.setProperty(error.getField(), error.getDefaultMessage()));
        return problem;
    }
}
```

### Segurança JWT

A segurança é o esquema da aula 05 adaptado às roles `AGENT` e `CLIENT`. As propriedades:

```java
package com.example.propertyplatform.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(List<User> users) {

    public record User(String username, String password, List<String> roles) {
    }
}
```

O `UserDetailsService` em memória e o `AuthenticationManager`, no formato da aula 05:

```java
package com.example.propertyplatform.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class UserStoreConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(SecurityProperties properties) {
        return new InMemoryUserDetailsManager(properties.users().stream()
                .map(this::toUserDetails)
                .toList());
    }

    private UserDetails toUserDetails(SecurityProperties.User user) {
        return User.withUsername(user.username())
                .password(user.password())
                .roles(user.roles().toArray(String[]::new))
                .build();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
```

A filtragem HTTP, o decoder e o encoder do JWT. `/api/auth/**` e o health ficam abertos; o resto exige token. CSRF desligado porque a API é stateless com token no header:

```java
package com.example.propertyplatform.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.jwt-secret}") String secret) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${app.security.jwt-secret}") String secret) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        var authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
```

O serviço que assina o token, sem o prefixo `ROLE_` na claim:

```java
package com.example.propertyplatform.security;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private final JwtEncoder encoder;

    public TokenService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String issue(String username, List<String> roles) {
        var now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("property-platform")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .subject(username)
                .claim("roles", roles)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
```

O endpoint de login:

```java
package com.example.propertyplatform.security;

import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(this::roleName)
                .toList();
        return new LoginResponse(tokenService.issue(authentication.getName(), roles));
    }

    private String roleName(String authority) {
        return authority.startsWith("ROLE_") ? authority.substring(5) : authority;
    }

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(String token) {
    }
}
```

### Teste

O teste arquitetural fica em `property-platform/src/test/java/com/example/propertyplatform/ArchitectureTest.java`. Para o JUnit 5 ser descoberto, a build precisa desta configuração da task de teste:

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
}
```

Sem ela, o `./gradlew test` não acha nenhum teste e responde `No matching tests found`. Rode dentro de `property-platform`:

```bash
./gradlew test
```

Com o teste verde, suba a aplicação (só o H2, nada externo) e use o `requests.http`. Os blocos de script salvam os tokens nas variáveis `agentToken` e `clientToken`:

```http
# property-platform/src/main/resources/requests.http

### Login do corretor
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{ "username": "agent", "password": "password" }

> {%
    client.global.set("agentToken", response.body.token);
%}

### Login do cliente
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{ "username": "client", "password": "password" }

> {%
    client.global.set("clientToken", response.body.token);
%}

### Cria anúncio
POST http://localhost:8080/api/properties
Authorization: Bearer {{agentToken}}
Content-Type: application/json

{
  "title": "Apartamento 2 quartos em Botafogo",
  "city": "Rio de Janeiro",
  "monthlyRent": 3200.00
}

### Lista anúncios
GET http://localhost:8080/api/properties
Authorization: Bearer {{clientToken}}

### Lista sem token
GET http://localhost:8080/api/properties

### Cliente tenta criar anúncio
POST http://localhost:8080/api/properties
Authorization: Bearer {{clientToken}}
Content-Type: application/json

{
  "title": "Casa",
  "city": "Niterói",
  "monthlyRent": 2000.00
}
```

Os resultados esperados:

- O `./gradlew test` passa com o `ArchitectureTest` verde.
- Login do corretor e do cliente respondem `200` com um JWT no campo `token`.
- `POST /api/properties` com `agentToken` responde `201` com `id` e `status: ACTIVE`.
- O mesmo request com `clientToken` responde `403`, porque a rota exige `ROLE_AGENT`.
- `GET /api/properties` com token responde `200` com a lista. Sem token responde `401`.

A Parte 2 acrescenta a visita.

## Parte 2 — Visita: eventos de domínio, Modulith e RabbitMQ

Agendar uma visita grava a visita e dispara reações: confirmar pro cliente e avisar o serviço de manutenção. Se o método de agendamento chamar tudo isso direto, o cadastro fica amarrado a serviços externos e a uma falha deles. O evento de domínio separa o fato (a visita foi agendada) de quem reage a ele (aula 07).

O `@ApplicationModuleListener` é o atalho de `@Transactional` + `@TransactionalEventListener` + `@Async`: reage depois do commit, em transação e thread próprias. O Event Publication Registry grava cada evento numa tabela `event_publication`; se o listener falha, a linha fica pendente e o evento é republicado no restart, porque `republish-outstanding-events-on-restart` está ligado no `application.yaml` (aula 07).

O RabbitMQ entra para atravessar a fronteira do processo. A mensagem vai para uma exchange, e o binding decide quais filas recebem pela routing key. O produtor conhece só a exchange e a chave. A DLQ recebe o que falha depois do retry (aula 07).

### Dependências

Acrescente ao build do `property-platform`:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-amqp")
```

O starter traz o `RabbitTemplate` e o listener container. O Modulith já está no build desde a Parte 1.

### Configuração

Mantenha este `application.yaml` completo em `property-platform/src/main/resources/application.yaml`:

```yaml
server:
  port: 8080
spring:
  application:
    name: property-platform
  datasource:
    url: jdbc:h2:mem:property;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
  modulith:
    events:
      republish-outstanding-events-on-restart: true
app:
  security:
    jwt-secret: ${JWT_SECRET:dev-secret-com-pelo-menos-32-caracteres}
    users:
      - username: agent
        password: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
        roles:
          - AGENT
      - username: client
        password: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
        roles:
          - CLIENT
```

O bloco `spring.rabbitmq` aponta para o broker local do compose.

### Infra local

O RabbitMQ sobe por Docker. Crie `docker-compose.yml` na pasta que contém os diretórios dos projetos:

```yaml
services:
  rabbitmq:
    image: rabbitmq:4-management
    ports:
      - "5672:5672"
      - "15672:15672"
```

O console fica em `http://localhost:15672` (guest/guest). Suba com `docker compose up -d` antes de rodar a aplicação, porque o listener container conecta na subida.

### Fronteira do módulo visit

O evento é a única API que o módulo `visit` publica. Em `visit/events/package-info.java`, o subpacote vira uma interface nomeada:

```java
@org.springframework.modulith.NamedInterface("events")
package com.example.propertyplatform.visit.events;
```

O evento carrega só o que aconteceu:

```java
package com.example.propertyplatform.visit.events;

import java.time.Instant;

public record VisitScheduled(Long visitId, Long propertyId, String visitorName, Instant scheduledAt) {
}
```

### Persistência e API da visita

A entidade:

```java
package com.example.propertyplatform.visit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "visits")
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private String visitorName;

    @Column(nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VisitStatus status;

    protected Visit() {
    }

    public Visit(Long propertyId, String visitorName, Instant scheduledAt) {
        this.propertyId = propertyId;
        this.visitorName = visitorName;
        this.scheduledAt = scheduledAt;
        this.status = VisitStatus.SCHEDULED;
    }

    public Long getId() {
        return id;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public String getVisitorName() {
        return visitorName;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public VisitStatus getStatus() {
        return status;
    }
}
```

```java
package com.example.propertyplatform.visit;

public enum VisitStatus {

    SCHEDULED,
    CONFIRMED
}
```

```java
package com.example.propertyplatform.visit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitRepository extends JpaRepository<Visit, Long> {
}
```

O serviço valida o imóvel pela API do módulo `listing`, grava a visita e publica o evento dentro da transação. O `ApplicationEventPublisher` entrega o evento ao Modulith só depois do commit:

```java
package com.example.propertyplatform.visit;

import java.util.List;

import com.example.propertyplatform.listing.PropertyCatalog;
import com.example.propertyplatform.visit.events.VisitScheduled;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitService {

    private final VisitRepository repository;

    private final PropertyCatalog propertyCatalog;

    private final ApplicationEventPublisher eventPublisher;

    public VisitService(
            VisitRepository repository,
            PropertyCatalog propertyCatalog,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.propertyCatalog = propertyCatalog;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Visit schedule(ScheduleVisitRequest request) {
        propertyCatalog.getById(request.propertyId());
        Visit visit = repository.save(new Visit(
                request.propertyId(), request.visitorName(), request.scheduledAt()));
        eventPublisher.publishEvent(new VisitScheduled(
                visit.getId(), visit.getPropertyId(), visit.getVisitorName(), visit.getScheduledAt()));
        return visit;
    }

    @Transactional(readOnly = true)
    public List<Visit> findAll() {
        return repository.findAll();
    }
}
```

Os records de entrada e saída:

```java
package com.example.propertyplatform.visit;

import java.time.Instant;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScheduleVisitRequest(
        @NotNull Long propertyId,
        @NotBlank String visitorName,
        @Future Instant scheduledAt) {
}
```

```java
package com.example.propertyplatform.visit;

import java.time.Instant;

public record VisitResponse(Long id, Long propertyId, String visitorName, Instant scheduledAt, String status) {
}
```

```java
package com.example.propertyplatform.visit;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/visits")
public class VisitController {

    private final VisitService visitService;

    public VisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VisitResponse schedule(@Valid @RequestBody ScheduleVisitRequest request) {
        Visit visit = visitService.schedule(request);
        return new VisitResponse(
                visit.getId(),
                visit.getPropertyId(),
                visit.getVisitorName(),
                visit.getScheduledAt(),
                visit.getStatus().name());
    }

    @GetMapping
    public List<VisitResponse> findAll() {
        return visitService.findAll().stream()
                .map(visit -> new VisitResponse(
                        visit.getId(),
                        visit.getPropertyId(),
                        visit.getVisitorName(),
                        visit.getScheduledAt(),
                        visit.getStatus().name()))
                .toList();
    }
}
```

### Topologia do RabbitMQ

A topologia fica no pacote raiz `config` e é declarada como beans. O conversor serializa os records em JSON (aula 07):

```java
package com.example.propertyplatform.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqTopologyConfiguration {

    @Bean
    MessageConverter jacksonAmqpMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    TopicExchange visitExchange() {
        return new TopicExchange("visit-exchange");
    }

    @Bean
    TopicExchange visitDeadLetterExchange() {
        return new TopicExchange("visit-dlx");
    }

    @Bean
    Queue visitScheduledQueue() {
        return QueueBuilder.durable("visit-scheduled")
                .deadLetterExchange("visit-dlx")
                .deadLetterRoutingKey("visit.scheduled.dead")
                .build();
    }

    @Bean
    Queue visitScheduledDeadLetterQueue() {
        return new Queue("visit-scheduled.dlq");
    }

    @Bean
    Binding visitScheduledBinding(Queue visitScheduledQueue, TopicExchange visitExchange) {
        return BindingBuilder.bind(visitScheduledQueue).to(visitExchange).with("visit.scheduled");
    }

    @Bean
    Binding visitScheduledDeadLetterBinding(
            Queue visitScheduledDeadLetterQueue, TopicExchange visitDeadLetterExchange) {
        return BindingBuilder.bind(visitScheduledDeadLetterQueue)
                .to(visitDeadLetterExchange)
                .with("visit.scheduled.dead");
    }
}
```

### Quem reage

O módulo `notification` confirma a visita no processo. Em `notification/package-info.java`, o módulo declara que só conhece o evento da visita:

```java
@org.springframework.modulith.ApplicationModule(allowedDependencies = "visit :: events")
package com.example.propertyplatform.notification;
```

O listener roda depois do commit, fora da transação do agendamento:

```java
package com.example.propertyplatform.notification;

import com.example.propertyplatform.visit.events.VisitScheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class VisitNotificationListener {

    private static final Logger logger = LoggerFactory.getLogger(VisitNotificationListener.class);

    @ApplicationModuleListener
    public void on(VisitScheduled event) {
        logger.info("Visita {} confirmada para {} no imóvel {}",
                event.visitId(), event.visitorName(), event.propertyId());
    }
}
```

O publicador RabbitMQ fica no próprio módulo `visit`, também como `@ApplicationModuleListener`. O evento só chega ao broker depois do commit da visita:

```java
package com.example.propertyplatform.visit;

import com.example.propertyplatform.visit.events.VisitScheduled;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class VisitScheduledRabbitPublisher {

    private final RabbitTemplate rabbitTemplate;

    public VisitScheduledRabbitPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @ApplicationModuleListener
    public void on(VisitScheduled event) {
        rabbitTemplate.convertAndSend("visit-exchange", "visit.scheduled", event);
    }
}
```

### Teste

Suba o RabbitMQ (`docker compose up -d` no diretório do compose) e o `property-platform` (`./gradlew bootRun`). Acrescente o request ao `requests.http` e rode:

```http
### Agenda visita
< {%
    const scheduledAt = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();
    request.variables.set("scheduledAt", scheduledAt);
%}
POST http://localhost:8080/api/visits
Authorization: Bearer {{clientToken}}
Content-Type: application/json

{
  "propertyId": 1,
  "visitorName": "Ana",
  "scheduledAt": "{{scheduledAt}}"
}
```

Resultados esperados:

- O `POST` responde `201` com `status: SCHEDULED`.
- O log do processo mostra a linha do `notification` confirmando a visita, com `[app,traceId,spanId]`.
- No console `http://localhost:15672`, a fila `visit-scheduled` tem uma mensagem pronta (o consumidor só entra na Parte 3, então ela fica retida).
- Um `propertyId` que não existe responde `404` com `ProblemDetail`.

Na Parte 3, o `maintenance-service` consome essa fila.

## Parte 3 — Manutenção: gRPC e integração entre processos

A manutenção tem dois caminhos. Quando o cliente abre um pedido pela API, o `property-platform` precisa do número da ordem na hora para devolver na resposta: essa chamada é síncrona e usa gRPC, com contrato tipado (aula 13). Quando uma visita é agendada, o `maintenance-service` reage de forma assíncrona pela fila RabbitMQ da Parte 2.

O gRPC separa o contrato da implementação. O `maintenance-contract` gera `MaintenanceServiceGrpc.MaintenanceServiceImplBase` e os stubs; o servidor estende a base e o cliente injeta um stub bloqueante. O `@GrpcService` registra o bean, e o canal aponta para `localhost:9090` por propriedade.

### O contrato

Crie o `maintenance-contract` como um projeto **Gradle** com **Kotlin** e nome `maintenance-contract`. Ele é uma biblioteca Java, não uma aplicação Spring Boot, então o build não aplica o plugin do Boot. Substitua todo o `build.gradle.kts` por:

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

O `java-library` traz a configuração `api`, que repassa as dependências do protobuf e do gRPC para quem consome o jar. O `maven-publish` publica o artefato. O `compileOnly` do `annotations-api` cobre o `@javax.annotation.Generated` que o código gerado carrega. As versões `4.35.1` e `1.83.1` são as gerenciadas pelo Spring Boot 4.1. A versão do projeto é `1.0.0`, e é essa que o consumidor vai pedir.

Mantenha o contrato em `maintenance-contract/src/main/proto/maintenance.proto`:

```protobuf
syntax = "proto3";

package maintenance;

option java_package = "com.example.maintenancecontract.api";
option java_multiple_files = true;

service MaintenanceService {
  rpc OpenServiceOrder(OpenServiceOrderRequest) returns (ServiceOrder);
  rpc GetServiceOrder(GetServiceOrderRequest) returns (ServiceOrder);
}

message OpenServiceOrderRequest {
  string property_id = 1;
  string description = 2;
  string priority = 3;
}

message GetServiceOrderRequest {
  string order_id = 1;
}

message ServiceOrder {
  string order_id = 1;
  string property_id = 2;
  string description = 3;
  string priority = 4;
  string status = 5;
  int64 opened_at_epoch_seconds = 6;
}
```

Publique o contrato dentro de `maintenance-contract`:

```bash
./gradlew publishToMavenLocal
```

O jar vai para `~/.m2/repository`, de onde `property-platform` e `maintenance-service` o consomem. Rode de novo a cada mudança no `.proto`.

### Dependências do property-platform

Acrescente ao build do `property-platform`:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-grpc-client")
implementation("com.example:maintenance-contract:1.0.0")
```

E acrescente `mavenLocal()` ao bloco `repositories`, antes de `mavenCentral()`, para o Gradle achar o contrato publicado.

### Configuração do property-platform

Mantenha este `application.yaml` completo:

```yaml
server:
  port: 8080
spring:
  application:
    name: property-platform
  datasource:
    url: jdbc:h2:mem:property;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
  modulith:
    events:
      republish-outstanding-events-on-restart: true
app:
  security:
    jwt-secret: ${JWT_SECRET:dev-secret-com-pelo-menos-32-caracteres}
    users:
      - username: agent
        password: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
        roles:
          - AGENT
      - username: client
        password: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
        roles:
          - CLIENT
  maintenance:
    grpc-target: localhost:9090
```

O `app.maintenance.grpc-target` é o endereço do servidor gRPC.

### Cliente gRPC

O stub nasce do `GrpcChannelFactory`, endereçado pela propriedade (aula 13):

```java
package com.example.propertyplatform.maintenance;

import com.example.maintenancecontract.api.MaintenanceServiceGrpc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class MaintenanceGrpcClientConfiguration {

    @Bean
    MaintenanceServiceGrpc.MaintenanceServiceBlockingStub maintenanceServiceStub(
            GrpcChannelFactory channels,
            @Value("${app.maintenance.grpc-target}") String grpcTarget) {
        return MaintenanceServiceGrpc.newBlockingStub(channels.createChannel(grpcTarget));
    }
}
```

O pedido de manutenção guarda o id da ordem devolvido pelo `maintenance-service`:

```java
package com.example.propertyplatform.maintenance;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "maintenance_requests")
public class MaintenanceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long propertyId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String priority;

    @Column(nullable = false)
    private String serviceOrderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaintenanceRequestStatus status;

    @Column(nullable = false)
    private Instant requestedAt;

    protected MaintenanceRequest() {
    }

    public MaintenanceRequest(Long propertyId, String description, String priority, String serviceOrderId) {
        this.propertyId = propertyId;
        this.description = description;
        this.priority = priority;
        this.serviceOrderId = serviceOrderId;
        this.status = MaintenanceRequestStatus.OPEN;
        this.requestedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getPropertyId() {
        return propertyId;
    }

    public String getDescription() {
        return description;
    }

    public String getPriority() {
        return priority;
    }

    public String getServiceOrderId() {
        return serviceOrderId;
    }

    public MaintenanceRequestStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }
}
```

```java
package com.example.propertyplatform.maintenance;

public enum MaintenanceRequestStatus {

    OPEN,
    CLOSED
}
```

```java
package com.example.propertyplatform.maintenance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> {

    List<MaintenanceRequest> findByPropertyId(Long propertyId);
}
```

Os records da rota:

```java
package com.example.propertyplatform.maintenance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OpenMaintenanceRequest(
        @NotNull Long propertyId,
        @NotBlank String description,
        @NotBlank String priority) {
}
```

```java
package com.example.propertyplatform.maintenance;

import java.time.Instant;

public record MaintenanceResponse(
        Long id,
        Long propertyId,
        String description,
        String priority,
        String serviceOrderId,
        String status,
        Instant requestedAt) {
}
```

O serviço chama o stub, converte a mensagem protobuf em dados locais e grava o pedido:

```java
package com.example.propertyplatform.maintenance;

import com.example.maintenancecontract.api.MaintenanceServiceGrpc;
import com.example.maintenancecontract.api.OpenServiceOrderRequest;
import com.example.maintenancecontract.api.ServiceOrder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceRequestService {

    private final MaintenanceRequestRepository repository;

    private final MaintenanceServiceGrpc.MaintenanceServiceBlockingStub maintenanceService;

    public MaintenanceRequestService(
            MaintenanceRequestRepository repository,
            MaintenanceServiceGrpc.MaintenanceServiceBlockingStub maintenanceService) {
        this.repository = repository;
        this.maintenanceService = maintenanceService;
    }

    @Transactional
    public MaintenanceRequest open(OpenMaintenanceRequest request) {
        ServiceOrder order = maintenanceService.openServiceOrder(OpenServiceOrderRequest.newBuilder()
                .setPropertyId(String.valueOf(request.propertyId()))
                .setDescription(request.description())
                .setPriority(request.priority())
                .build());
        return repository.save(new MaintenanceRequest(
                request.propertyId(), request.description(), request.priority(), order.getOrderId()));
    }
}
```

O controlador expõe a abertura do pedido:

```java
package com.example.propertyplatform.maintenance;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenance-requests")
public class MaintenanceController {

    private final MaintenanceRequestService maintenanceRequestService;

    public MaintenanceController(MaintenanceRequestService maintenanceRequestService) {
        this.maintenanceRequestService = maintenanceRequestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceResponse open(@Valid @RequestBody OpenMaintenanceRequest request) {
        MaintenanceRequest maintenanceRequest = maintenanceRequestService.open(request);
        return new MaintenanceResponse(
                maintenanceRequest.getId(),
                maintenanceRequest.getPropertyId(),
                maintenanceRequest.getDescription(),
                maintenanceRequest.getPriority(),
                maintenanceRequest.getServiceOrderId(),
                maintenanceRequest.getStatus().name(),
                maintenanceRequest.getRequestedAt());
    }
}
```

Com isso o cliente compila. O servidor gRPC entra a seguir.

### O maintenance-service

Crie o `maintenance-service` como um projeto **Gradle** com **Kotlin** e nome `maintenance-service`. Acrescente ao build:

```kotlin
implementation("org.springframework.boot:spring-boot-starter")
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-data-jpa")
implementation("org.springframework.boot:spring-boot-starter-amqp")
implementation("org.springframework.boot:spring-boot-starter-grpc-server")
implementation("io.grpc:grpc-services")
implementation("com.example:maintenance-contract:1.0.0")
runtimeOnly("com.h2database:h2")
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

Acrescente `mavenLocal()` ao bloco `repositories`. O `grpc-server` sobe o servidor Netty e expõe todo bean `BindableService` (aula 13). O `grpc-services` habilita o reflection, que a IDE usa para ler o contrato. O `webmvc` existe para o Actuator responder HTTP na porta de gestão. O `amqp` consome os eventos de visita.

Mantenha este `application.yaml` completo em `maintenance-service/src/main/resources/application.yaml`:

```yaml
server:
  port: 8081
spring:
  application:
    name: maintenance-service
  grpc:
    server:
      port: 9090
  datasource:
    url: jdbc:h2:mem:maintenance;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    listener:
      simple:
        default-requeue-rejected: false
```

O gRPC atende em `9090`; a gestão HTTP responde em `8081`, no mesmo processo. O `default-requeue-rejected: false` faz uma mensagem que falha no listener ser rejeitada sem requeue, caindo na DLQ.

A classe principal:

```java
package com.example.maintenanceservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MaintenanceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MaintenanceServiceApplication.class, args);
    }
}
```

A ordem de serviço persistida. O nome `WorkOrder` evita colisão com a mensagem `ServiceOrder` do contrato:

```java
package com.example.maintenanceservice.orders;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "work_orders")
public class WorkOrder {

    @Id
    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private String propertyId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkOrderStatus status;

    @Column(nullable = false)
    private Instant openedAt;

    protected WorkOrder() {
    }

    public WorkOrder(String orderId, String propertyId, String description, String priority) {
        this.orderId = orderId;
        this.propertyId = propertyId;
        this.description = description;
        this.priority = priority;
        this.status = WorkOrderStatus.OPEN;
        this.openedAt = Instant.now();
    }

    public String getOrderId() {
        return orderId;
    }

    public String getPropertyId() {
        return propertyId;
    }

    public String getDescription() {
        return description;
    }

    public String getPriority() {
        return priority;
    }

    public WorkOrderStatus getStatus() {
        return status;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }
}
```

```java
package com.example.maintenanceservice.orders;

public enum WorkOrderStatus {

    OPEN,
    DONE
}
```

```java
package com.example.maintenanceservice.orders;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, String> {
}
```

O serviço gera o id da ordem e grava:

```java
package com.example.maintenanceservice.orders;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkOrderService {

    private final WorkOrderRepository repository;

    public WorkOrderService(WorkOrderRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public WorkOrder open(String propertyId, String description, String priority) {
        String orderId = "OS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return repository.save(new WorkOrder(orderId, propertyId, description, priority));
    }

    @Transactional(readOnly = true)
    public Optional<WorkOrder> find(String orderId) {
        return repository.findById(orderId);
    }
}
```

O `@GrpcService` estende a base gerada e converte entre a entidade e a mensagem protobuf. Ordem desconhecida devolve `NOT_FOUND` (aula 13):

```java
package com.example.maintenanceservice.grpc;

import com.example.maintenancecontract.api.GetServiceOrderRequest;
import com.example.maintenancecontract.api.MaintenanceServiceGrpc;
import com.example.maintenancecontract.api.OpenServiceOrderRequest;
import com.example.maintenancecontract.api.ServiceOrder;
import com.example.maintenanceservice.orders.WorkOrder;
import com.example.maintenanceservice.orders.WorkOrderService;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class MaintenanceGrpcEndpoint extends MaintenanceServiceGrpc.MaintenanceServiceImplBase {

    private final WorkOrderService workOrderService;

    public MaintenanceGrpcEndpoint(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @Override
    public void openServiceOrder(OpenServiceOrderRequest request, StreamObserver<ServiceOrder> responseObserver) {
        WorkOrder workOrder = workOrderService.open(
                request.getPropertyId(), request.getDescription(), request.getPriority());
        responseObserver.onNext(toMessage(workOrder));
        responseObserver.onCompleted();
    }

    @Override
    public void getServiceOrder(GetServiceOrderRequest request, StreamObserver<ServiceOrder> responseObserver) {
        workOrderService.find(request.getOrderId()).ifPresentOrElse(
                workOrder -> {
                    responseObserver.onNext(toMessage(workOrder));
                    responseObserver.onCompleted();
                },
                () -> responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Ordem não encontrada")
                        .asRuntimeException()));
    }

    private static ServiceOrder toMessage(WorkOrder workOrder) {
        return ServiceOrder.newBuilder()
                .setOrderId(workOrder.getOrderId())
                .setPropertyId(workOrder.getPropertyId())
                .setDescription(workOrder.getDescription())
                .setPriority(workOrder.getPriority())
                .setStatus(workOrder.getStatus().name())
                .setOpenedAtEpochSeconds(workOrder.getOpenedAt().getEpochSecond())
                .build();
    }
}
```

### Consumo da fila de visita

O `maintenance-service` consome a fila `visit-scheduled` que o `property-platform` alimenta. Cada processo tem o próprio record da mensagem: o contrato entre eles é o JSON, não a classe Java. O listener infere o tipo pelo parâmetro do método, então o `__TypeId__` enviado pelo produtor é ignorado (aula 07):

```java
package com.example.maintenanceservice.messaging;

import java.time.Instant;

public record VisitScheduledMessage(Long visitId, Long propertyId, String visitorName, Instant scheduledAt) {
}
```

```java
package com.example.maintenanceservice.messaging;

import com.example.maintenanceservice.orders.WorkOrderService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class VisitScheduledListener {

    private static final Logger logger = LoggerFactory.getLogger(VisitScheduledListener.class);

    private final WorkOrderService workOrderService;

    public VisitScheduledListener(WorkOrderService workOrderService) {
        this.workOrderService = workOrderService;
    }

    @RabbitListener(queues = "visit-scheduled")
    public void on(VisitScheduledMessage message) {
        workOrderService.open(
                String.valueOf(message.propertyId()),
                "Vistoria preventiva antes da visita " + message.visitId(),
                "LOW");
        logger.info("Vistoria preventiva aberta para o imóvel {}", message.propertyId());
    }
}
```

O `maintenance-service` declara a mesma fila para conseguir subir antes do `property-platform`. Declarações RabbitMQ são idempotentes quando iguais:

```java
package com.example.maintenanceservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AmqpConfiguration {

    @Bean
    MessageConverter jacksonAmqpMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    TopicExchange visitExchange() {
        return new TopicExchange("visit-exchange");
    }

    @Bean
    Queue visitScheduledQueue() {
        return QueueBuilder.durable("visit-scheduled")
                .deadLetterExchange("visit-dlx")
                .deadLetterRoutingKey("visit.scheduled.dead")
                .build();
    }

    @Bean
    Binding visitScheduledBinding(Queue visitScheduledQueue, TopicExchange visitExchange) {
        return BindingBuilder.bind(visitScheduledQueue).to(visitExchange).with("visit.scheduled");
    }
}
```

### Teste

Com o `maintenance-service` de pé (`./gradlew bootRun` no diretório dele) e o `property-platform`, acrescente o request de manutenção ao `requests.http`:

```http
### Abre pedido de manutenção
POST http://localhost:8080/api/maintenance-requests
Authorization: Bearer {{clientToken}}
Content-Type: application/json

{
  "propertyId": 1,
  "description": "Vazamento na cozinha",
  "priority": "HIGH"
}
```

O gRPC também pode ser chamado direto no `maintenance-service`, sem passar pelo `property-platform`. Os plugins **Protocol Buffers** e **gRPC** do IntelliJ precisam estar ligados (aula 13):

```http
# maintenance-service/src/main/resources/requests.http

### Abre ordem direto no gRPC
GRPC localhost:9090/maintenance.MaintenanceService/OpenServiceOrder

{
  "propertyId": "1",
  "description": "Troca de fechadura",
  "priority": "MEDIUM"
}

### Ordem inexistente
GRPC localhost:9090/maintenance.MaintenanceService/GetServiceOrder

{
  "orderId": "OS-NAOEXISTE"
}
```

Resultados esperados:

- O `POST /api/maintenance-requests` responde `201` com `serviceOrderId` no formato `OS-XXXXXXXX` e `status: OPEN`.
- O `OpenServiceOrder` direto devolve uma `ServiceOrder` com `status: OPEN` e id novo; o `GetServiceOrder` de uma ordem inexistente devolve `NotFound`.
- Agende uma visita: o log do `maintenance-service` mostra a vistoria preventiva aberta, porque o listener consome a fila da Parte 2.
- Com o `maintenance-service` parado, o `POST /api/maintenance-requests` falha por erro de gRPC e a mensagem da visita fica na fila até o serviço voltar.

## Parte 4 — Assistente: Spring AI com Ollama

O assistente responde dúvidas do cliente, busca imóveis disponíveis e consulta as políticas da imobiliária. O `ChatClient` é a fachada fluente sobre o modelo; o `@Tool` deixa o modelo pedir dados internos; o `QuestionAnswerAdvisor` roda o RAG sobre as políticas (aula 14).

O provedor é o Ollama local em `http://localhost:11434`. Instale pelo site oficial e garanta o daemon de pé. Em outro terminal, baixe os dois modelos:

```bash
ollama pull qwen2.5:7b
ollama pull nomic-embed-text
```

O `qwen2.5:7b` atende o chat e o tool calling; o `nomic-embed-text` gera os embeddings do RAG. O contexto do `property-platform` sobe sem o Ollama: o modelo só é chamado quando uma rota do assistente é usada.

### Dependências

Acrescente ao build do `property-platform`:

```kotlin
implementation(platform("org.springframework.ai:spring-ai-bom:2.0.1"))
implementation("org.springframework.ai:spring-ai-starter-model-ollama")
implementation("org.springframework.ai:spring-ai-vector-store")
implementation("org.springframework.ai:spring-ai-vector-store-advisor")
```

O BOM `spring-ai-bom:2.0.1` casa o Spring AI 2.0.1 com o Spring Boot 4.1. O starter do Ollama cria o `ChatModel`, o `EmbeddingModel`, um `ChatMemory` em memória e o `ChatClient.Builder`. O `spring-ai-vector-store` traz o `SimpleVectorStore` e o `SearchRequest`; o `spring-ai-vector-store-advisor` traz o `QuestionAnswerAdvisor`.

### Configuração

Mantenha este `application.yaml` completo:

```yaml
server:
  port: 8080
spring:
  application:
    name: property-platform
  datasource:
    url: jdbc:h2:mem:property;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
  modulith:
    events:
      republish-outstanding-events-on-restart: true
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: qwen2.5:7b
        temperature: 0.3
      embedding:
        model: nomic-embed-text
app:
  security:
    jwt-secret: ${JWT_SECRET:dev-secret-com-pelo-menos-32-caracteres}
    users:
      - username: agent
        password: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
        roles:
          - AGENT
      - username: client
        password: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
        roles:
          - CLIENT
  maintenance:
    grpc-target: localhost:9090
```

O `base-url` aponta para o Ollama local. O `chat.model` escolhe o modelo; a `temperature` baixa deixa a resposta mais estável nas chamadas de ferramenta. O `embedding.model` gera os vetores.

### O ChatClient e o prompt de sistema

O `ChatClient` nasce numa configuração própria, com o prompt de sistema que vale para toda chamada. O starter do Ollama já monta o `ChatClient.Builder`:

```java
package com.example.propertyplatform.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssistantConfiguration {

    @Bean
    ChatClient propertyChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("""
                        Você é o assistente da imobiliária Morada Certa.
                        Responda em português, de forma curta e direta.
                        Use as ferramentas quando precisar de dados de imóveis.
                        Quando houver contexto recuperado, baseie a resposta nele.
                        Se não souber, diga que não sabe.
                        """)
                .build();
    }
}
```

### Tool calling

A ferramenta consulta o catálogo do módulo `listing` pela API pública. O modelo vê a descrição, decide quando chamar e o Spring AI executa o método:

```java
package com.example.propertyplatform.assistant;

import java.math.BigDecimal;
import java.util.List;

import com.example.propertyplatform.listing.PropertyCatalog;
import com.example.propertyplatform.listing.PropertySummary;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class PropertyTools {

    private final PropertyCatalog propertyCatalog;

    public PropertyTools(PropertyCatalog propertyCatalog) {
        this.propertyCatalog = propertyCatalog;
    }

    @Tool(description = "Lista imóveis disponíveis para aluguel por cidade e teto de aluguel mensal")
    public List<PropertySummary> findAvailable(
            @ToolParam(description = "Cidade do imóvel; use vazio para qualquer cidade") String city,
            @ToolParam(description = "Aluguel mensal máximo em reais") double maxMonthlyRent) {
        return propertyCatalog.findAvailable(city, BigDecimal.valueOf(maxMonthlyRent));
    }
}
```

### RAG das políticas

As políticas da imobiliária viram documentos do vector store. O `SimpleVectorStore` guarda os vetores em memória e compara por similaridade de cosseno (aula 14). O bean nasce vazio; o índice é montado na primeira chamada, para o processo não depender do Ollama na subida:

```java
package com.example.propertyplatform.assistant;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeConfiguration {

    @Bean
    VectorStore policyVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
```

```java
package com.example.propertyplatform.assistant;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

@Component
public class PolicyIndexer {

    private final VectorStore vectorStore;

    private final Object lock = new Object();

    private volatile boolean indexed;

    public PolicyIndexer(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void ensureIndexed() {
        if (indexed) {
            return;
        }
        synchronized (lock) {
            if (!indexed) {
                vectorStore.add(policyDocuments());
                indexed = true;
            }
        }
    }

    private static List<Document> policyDocuments() {
        return List.of(
                Document.builder()
                        .text("A visita é acompanhada por um corretor e pode ser agendada com 24 horas de antecedência.")
                        .metadata("source", "politica-de-visita")
                        .build(),
                Document.builder()
                        .text("O aluguel exige caução de três meses, devolvida ao fim do contrato sem danos ao imóvel.")
                        .metadata("source", "politica-de-caucao")
                        .build(),
                Document.builder()
                        .text("Animais de pequeno porte são permitidos quando o condomínio autoriza.")
                        .metadata("source", "politica-de-animais")
                        .build(),
                Document.builder()
                        .text("Pedidos de manutenção emergencial são atendidos em até 24 horas.")
                        .metadata("source", "politica-de-manutencao")
                        .build());
    }
}
```

O resultado cru da busca vira record, com o `score` da similaridade:

```java
package com.example.propertyplatform.assistant;

public record KnowledgeMatch(String text, String source, Double score) {
}
```

```java
package com.example.propertyplatform.assistant;

import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeService {

    private final VectorStore policyVectorStore;

    private final PolicyIndexer policyIndexer;

    public KnowledgeService(VectorStore policyVectorStore, PolicyIndexer policyIndexer) {
        this.policyVectorStore = policyVectorStore;
        this.policyIndexer = policyIndexer;
    }

    public List<KnowledgeMatch> search(String question) {
        policyIndexer.ensureIndexed();
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(3)
                .build();
        return policyVectorStore.similaritySearch(searchRequest).stream()
                .map(KnowledgeService::toMatch)
                .toList();
    }

    private static KnowledgeMatch toMatch(Document document) {
        return new KnowledgeMatch(
                document.getText(),
                String.valueOf(document.getMetadata().get("source")),
                document.getScore());
    }
}
```

### O serviço e as rotas

O serviço junta memória de conversa, tool calling e RAG. O `MessageChatMemoryAdvisor` guarda o histórico por `conversationId`; o `QuestionAnswerAdvisor` injeta os trechos recuperados no prompt (aula 14):

```java
package com.example.propertyplatform.assistant;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class AssistantService {

    private final ChatClient chatClient;

    private final PropertyTools propertyTools;

    private final PolicyIndexer policyIndexer;

    private final MessageChatMemoryAdvisor chatMemoryAdvisor;

    private final QuestionAnswerAdvisor questionAnswerAdvisor;

    public AssistantService(
            ChatClient chatClient,
            PropertyTools propertyTools,
            PolicyIndexer policyIndexer,
            ChatMemory chatMemory,
            VectorStore policyVectorStore) {
        this.chatClient = chatClient;
        this.propertyTools = propertyTools;
        this.policyIndexer = policyIndexer;
        this.chatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        this.questionAnswerAdvisor = QuestionAnswerAdvisor.builder(policyVectorStore).build();
    }

    public String chat(String conversationId, String message) {
        return chatClient.prompt()
                .user(message)
                .advisors(chatMemoryAdvisor)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }

    public String findProperties(String message) {
        return chatClient.prompt()
                .user(message)
                .tools(propertyTools)
                .call()
                .content();
    }

    public String askFaq(String question) {
        policyIndexer.ensureIndexed();
        return chatClient.prompt()
                .user(question)
                .advisors(questionAnswerAdvisor)
                .call()
                .content();
    }
}
```

O controlador expõe as quatro rotas:

```java
package com.example.propertyplatform.assistant;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    private final KnowledgeService knowledgeService;

    public AssistantController(AssistantService assistantService, KnowledgeService knowledgeService) {
        this.assistantService = assistantService;
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/chat")
    public AssistantAnswer chat(@RequestBody ChatRequest request) {
        return new AssistantAnswer(assistantService.chat(request.conversationId(), request.message()));
    }

    @PostMapping("/properties")
    public AssistantAnswer properties(@RequestBody AssistantRequest request) {
        return new AssistantAnswer(assistantService.findProperties(request.message()));
    }

    @PostMapping("/faq")
    public AssistantAnswer faq(@RequestBody FaqRequest request) {
        return new AssistantAnswer(assistantService.askFaq(request.question()));
    }

    @GetMapping("/search")
    public List<KnowledgeMatch> search(@RequestParam("question") String question) {
        return knowledgeService.search(question);
    }

    public record ChatRequest(String conversationId, String message) {
    }

    public record AssistantRequest(String message) {
    }

    public record FaqRequest(String question) {
    }

    public record AssistantAnswer(String answer) {
    }
}
```

### Teste

O módulo `assistant` depende da API pública do `listing` e nada mais. Com o Ollama e os dois modelos de pé, acrescente os requests ao `requests.http`:

```http
### Assistente busca imóveis
POST http://localhost:8080/api/assistant/properties
Authorization: Bearer {{clientToken}}
Content-Type: application/json

{ "message": "Tem apartamento de até 3500 no Rio de Janeiro?" }

### Assistente responde política
POST http://localhost:8080/api/assistant/faq
Authorization: Bearer {{clientToken}}
Content-Type: application/json

{ "question": "Posso levar meu cachorro?" }

### Busca no vector store, sem modelo
GET http://localhost:8080/api/assistant/search?question=Qual a política de visita?
Authorization: Bearer {{clientToken}}

### Chat com memória
POST http://localhost:8080/api/assistant/chat
Authorization: Bearer {{clientToken}}
Content-Type: application/json

{
  "conversationId": "cliente-ana",
  "message": "Meu nome é Ana. Recomende um imóvel."
}
```

Resultados esperados:

- `POST /api/assistant/properties` responde `200` com uma frase citando o imóvel de Botafogo; o modelo chamou `findAvailable`.
- `POST /api/assistant/faq` responde `200` falando de animais de pequeno porte quando o condomínio autoriza.
- `GET /api/assistant/search` traz o documento `politica-de-visita` no topo, com o maior `score`. Esse request calcula o embedding da pergunta, então também precisa do Ollama.
- O `chat` responde `200` com uma recomendação em português. Se o Ollama estiver fora do ar, as rotas do assistente respondem `500`.

## Parte 5 — Relatório mensal: Spring Batch

O relatório mensal lê todas as visitas de um mês e grava uma linha por visita numa tabela de relatório. Esse volume não combina com uma requisição HTTP: o processamento é dividido em chunks transacionais e o estado fica no `JobRepository` para retomada (aula 12).

O `JobOperator` dispara o job por um endpoint. O `Job` tem um `Step` chunked com `JdbcPagingItemReader` lendo a tabela `visits` (da Parte 2), um `ItemProcessor` que deriva o mês e um `ItemWriter` que grava.

### Dependências

Acrescente ao build do `property-platform`:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-batch")
implementation("org.springframework.boot:spring-boot-starter-batch-jdbc")
implementation("org.springframework.boot:spring-boot-starter-jdbc")
```

O `batch` fornece Job, Step e chunk. O `batch-jdbc` ativa o `JobRepository` baseado em JDBC e a inicialização das tabelas de metadata. O `jdbc` dá acesso ao `DataSource` usado pelo Batch e pelo writer.

### Configuração

Mantenha este `application.yaml` completo:

```yaml
server:
  port: 8080
spring:
  application:
    name: property-platform
  datasource:
    url: jdbc:h2:mem:property;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
  batch:
    jdbc:
      initialize-schema: embedded
    job:
      enabled: false
  modulith:
    events:
      republish-outstanding-events-on-restart: true
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: qwen2.5:7b
        temperature: 0.3
      embedding:
        model: nomic-embed-text
app:
  security:
    jwt-secret: ${JWT_SECRET:dev-secret-com-pelo-menos-32-caracteres}
    users:
      - username: agent
        password: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
        roles:
          - AGENT
      - username: client
        password: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
        roles:
          - CLIENT
  maintenance:
    grpc-target: localhost:9090
```

O `initialize-schema: embedded` faz o Batch criar as tabelas de metadata no H2. O `job.enabled: false` impede o Boot de rodar o job do relatório na subida; o `JobOperator` dispara pelo endpoint.

### Tabela do relatório

O Hibernate não cria a tabela de relatório porque ela não é uma entidade. Ela nasce de um `schema.sql`, que o Boot roda em banco embarcado:

```sql
-- property-platform/src/main/resources/schema.sql
CREATE TABLE monthly_report_lines (
    visit_id BIGINT PRIMARY KEY,
    report_month VARCHAR(7) NOT NULL,
    property_id BIGINT NOT NULL,
    visitor_name VARCHAR(255) NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
```

### Reader, processor e writer

A linha lida do banco usa `java.sql.Timestamp`, que o mapeamento JDBC converte sem ajuste:

```java
package com.example.propertyplatform.reporting;

import java.sql.Timestamp;

public record MonthlyVisitRow(Long id, Long propertyId, String visitorName, Timestamp scheduledAt) {
}
```

A linha do relatório carrega o mês já calculado:

```java
package com.example.propertyplatform.reporting;

import java.sql.Timestamp;

public record MonthlyReportLine(
        Long visitId,
        String reportMonth,
        Long propertyId,
        String visitorName,
        Timestamp scheduledAt) {
}
```

O processor normaliza o nome e deriva o mês da data agendada. Nome vazio vira `IllegalStateException`, que o step trata como `skip`:

```java
package com.example.propertyplatform.reporting;

import java.time.YearMonth;
import java.time.ZoneOffset;

import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Component
public class MonthlyReportProcessor implements ItemProcessor<MonthlyVisitRow, MonthlyReportLine> {

    @Override
    public MonthlyReportLine process(MonthlyVisitRow row) {
        String visitorName = row.visitorName() == null ? "" : row.visitorName().trim();
        if (visitorName.isEmpty()) {
            throw new IllegalStateException("Visita sem nome de visitante: " + row.id());
        }
        YearMonth month = YearMonth.from(row.scheduledAt().toInstant().atZone(ZoneOffset.UTC));
        return new MonthlyReportLine(
                row.id(), month.toString(), row.propertyId(), visitorName, row.scheduledAt());
    }
}
```

O writer grava o chunk inteiro dentro da transação do step. O `MERGE` por `visit_id` deixa a retomada idempotente (aula 12):

```java
package com.example.propertyplatform.reporting;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class MonthlyReportWriter implements ItemWriter<MonthlyReportLine> {

    private final JdbcClient jdbcClient;

    public MonthlyReportWriter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void write(Chunk<? extends MonthlyReportLine> chunk) {
        for (MonthlyReportLine line : chunk) {
            jdbcClient.sql("""
                    MERGE INTO monthly_report_lines
                        (visit_id, report_month, property_id, visitor_name, scheduled_at, generated_at)
                    KEY (visit_id)
                    VALUES (:visitId, :reportMonth, :propertyId, :visitorName, :scheduledAt, :generatedAt)
                    """)
                    .param("visitId", line.visitId())
                    .param("reportMonth", line.reportMonth())
                    .param("propertyId", line.propertyId())
                    .param("visitorName", line.visitorName())
                    .param("scheduledAt", line.scheduledAt())
                    .param("generatedAt", Timestamp.from(Instant.now()))
                    .update();
        }
    }
}
```

A sintaxe `MERGE ... KEY` é a do H2 usado nesta aula. Em outro banco, mantenha a regra de idempotência e adapte o upsert ao dialeto (aula 12).

### Job e Step

O `@StepScope` no bean do reader dá acesso ao `jobParameters` no momento da execução. O mês chega no formato `2026-09` e vira o intervalo `[início do mês, início do mês seguinte)`:

```java
package com.example.propertyplatform.reporting;

import java.sql.Timestamp;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class MonthlyReportJobConfiguration {

    @Bean
    Job monthlyReportJob(JobRepository jobRepository, Step monthlyReportStep) {
        return new JobBuilder("monthlyReportJob", jobRepository)
                .start(monthlyReportStep)
                .build();
    }

    @Bean
    Step monthlyReportStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            JdbcPagingItemReader<MonthlyVisitRow> monthlyVisitReader,
            ItemProcessor<MonthlyVisitRow, MonthlyReportLine> processor,
            ItemWriter<MonthlyReportLine> writer) {
        return new StepBuilder("monthlyReportStep", jobRepository)
                .<MonthlyVisitRow, MonthlyReportLine>chunk(50)
                .transactionManager(transactionManager)
                .reader(monthlyVisitReader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skip(IllegalStateException.class)
                .skipLimit(10)
                .build();
    }

    @Bean
    @StepScope
    JdbcPagingItemReader<MonthlyVisitRow> monthlyVisitReader(
            DataSource dataSource,
            @Value("#{jobParameters['month']}") String month) {
        YearMonth yearMonth = YearMonth.parse(month);
        Timestamp start = Timestamp.from(yearMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        Timestamp end = Timestamp.from(yearMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        return new JdbcPagingItemReaderBuilder<MonthlyVisitRow>()
                .name("monthlyVisitReader")
                .dataSource(dataSource)
                .selectClause("SELECT id, property_id, visitor_name, scheduled_at")
                .fromClause("FROM visits")
                .whereClause("WHERE scheduled_at >= :start AND scheduled_at < :end")
                .sortKeys(Map.of("id", Order.ASCENDING))
                .parameterValues(Map.of("start", start, "end", end))
                .rowMapper(new DataClassRowMapper<>(MonthlyVisitRow.class))
                .pageSize(100)
                .build();
    }
}
```

O bean `ItemProcessor` é o `MonthlyReportProcessor` e o `ItemWriter` é o `MonthlyReportWriter`, ambos `@Component`. O `<MonthlyVisitRow, MonthlyReportLine>chunk(50)` casa os tipos.

### Disparo e status

O runner usa o `JobOperator` e o mês como parâmetro idempotente (aula 12):

```java
package com.example.propertyplatform.reporting;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.stereotype.Service;

@Service
public class MonthlyReportRunner {

    private final JobOperator jobOperator;

    private final Job monthlyReportJob;

    public MonthlyReportRunner(JobOperator jobOperator, Job monthlyReportJob) {
        this.jobOperator = jobOperator;
        this.monthlyReportJob = monthlyReportJob;
    }

    public JobExecution run(String month) throws JobExecutionException {
        return jobOperator.start(monthlyReportJob, new JobParametersBuilder()
                .addString("month", month)
                .toJobParameters());
    }
}
```

```java
package com.example.propertyplatform.reporting;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/monthly")
public class MonthlyReportController {

    private final MonthlyReportRunner runner;

    private final JobRepository jobRepository;

    public MonthlyReportController(MonthlyReportRunner runner, JobRepository jobRepository) {
        this.runner = runner;
        this.jobRepository = jobRepository;
    }

    @PostMapping("/{month}")
    public ResponseEntity<JobStatus> run(@PathVariable String month) throws JobExecutionException {
        JobExecution execution = runner.run(month);
        return ResponseEntity.ok(new JobStatus(execution.getId(), execution.getStatus().name()));
    }

    @GetMapping("/runs/{executionId}")
    public ResponseEntity<String> status(@PathVariable Long executionId) {
        JobExecution execution = jobRepository.getJobExecution(executionId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(execution.getStatus().name());
    }

    public record JobStatus(Long executionId, String status) {
    }
}
```

### Teste

Com o `property-platform` de pé e pelo menos uma visita agendada no mês, acrescente os requests ao `requests.http`:

```http
### Dispara o relatório mensal
POST http://localhost:8080/api/reports/monthly/2026-09
Authorization: Bearer {{agentToken}}

> {%
    client.global.set("executionId", response.body.executionId);
%}

### Consulta o status do relatório
GET http://localhost:8080/api/reports/monthly/runs/{{executionId}}
Authorization: Bearer {{agentToken}}
```

Resultados esperados:

- O `POST` responde `200` com `executionId` e `status: COMPLETED`; o `GET` seguinte confirma `COMPLETED`.
- Use o mês das visitas agendadas; um mês sem visitas conclui com zero linhas.
- Um `executionId` inexistente responde `404`.
- Rodar o mesmo mês de novo não cria outra execução, porque o mês é o parâmetro idempotente do job.

A partir daqui o tópico de observabilidade entra para medir e enxergar os dois processos.

## Parte 6 — Observabilidade dos dois processos

O Actuator expõe saúde, prontidão e métricas; o `spring-boot-starter-opentelemetry` unifica métricas, traces e logs em OTLP para o stack LGTM (aula 08). As métricas de infra (JVM, HTTP, pool) o Boot coleta sozinho; as de negócio entram no código dos serviços. O `@Observed` gera timer e span na mesma chamada (aula 08).

### Dependências

Acrescente ao build do `property-platform`:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
implementation("org.springframework.boot:spring-boot-starter-aspectj")
```

O `aspectj` é o weaver que processa o `@Observed`. Acrescente ao build do `maintenance-service`:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-actuator")
implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
```

### Configuração do property-platform

Mantenha este `application.yaml` completo:

```yaml
server:
  port: 8080
spring:
  application:
    name: property-platform
  datasource:
    url: jdbc:h2:mem:property;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
  batch:
    jdbc:
      initialize-schema: embedded
    job:
      enabled: false
  modulith:
    events:
      republish-outstanding-events-on-restart: true
  ai:
    ollama:
      base-url: http://localhost:11434
      chat:
        model: qwen2.5:7b
        temperature: 0.3
      embedding:
        model: nomic-embed-text
app:
  security:
    jwt-secret: ${JWT_SECRET:dev-secret-com-pelo-menos-32-caracteres}
    users:
      - username: agent
        password: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
        roles:
          - AGENT
      - username: client
        password: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
        roles:
          - CLIENT
  maintenance:
    grpc-target: localhost:9090
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  tracing:
    sampling:
      probability: 1.0
  otlp:
    metrics:
      export:
        url: ${OTLP_METRICS_URL:http://localhost:4318/v1/metrics}
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: ${OTLP_TRACES_URL:http://localhost:4318/v1/traces}
    logging:
      export:
        otlp:
          endpoint: ${OTLP_LOGS_URL:http://localhost:4318/v1/logs}
  observations:
    annotations:
      enabled: true
```

O `show-details: always` é pra dev. O `probes.enabled: true` liga liveness e readiness. A amostragem `1.0` pega todos os traces em dev; em produção cai pra `0.1`. Os três endpoints OTLP apontam para o mesmo container. Sem o `observations.annotations.enabled: true`, o `@Observed` não processa nada.

### Configuração do maintenance-service

Mantenha este `application.yaml` completo:

```yaml
server:
  port: 8081
spring:
  application:
    name: maintenance-service
  grpc:
    server:
      port: 9090
  datasource:
    url: jdbc:h2:mem:maintenance;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    listener:
      simple:
        default-requeue-rejected: false
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  tracing:
    sampling:
      probability: 1.0
  otlp:
    metrics:
      export:
        url: ${OTLP_METRICS_URL:http://localhost:4318/v1/metrics}
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: ${OTLP_TRACES_URL:http://localhost:4318/v1/traces}
    logging:
      export:
        otlp:
          endpoint: ${OTLP_LOGS_URL:http://localhost:4318/v1/logs}
```

### Stack de observabilidade

O `docker-compose.yml` ganha o stack LGTM, que junta Prometheus, Tempo, Loki e Grafana num container:

```yaml
services:
  rabbitmq:
    image: rabbitmq:4-management
    ports:
      - "5672:5672"
      - "15672:15672"

  otel-lgtm:
    image: grafana/otel-lgtm:latest
    ports:
      - "3000:3000"
      - "4317:4317"
      - "4318:4318"
```

Suba com `docker compose up -d`. O Grafana fica em `http://localhost:3000` (admin/admin na primeira vez).

### Métricas de negócio

Cada serviço ganha o contador da sua métrica. O `PropertyService` também ganha o `@Observed`, que vira timer e span. Mantenha estas três classes:

```java
package com.example.propertyplatform.listing;

import java.math.BigDecimal;
import java.util.List;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.annotation.Observed;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PropertyService implements PropertyCatalog {

    private final PropertyRepository repository;

    private final Counter createdListings;

    public PropertyService(PropertyRepository repository, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.createdListings = Counter.builder("property.listings.created")
                .description("Anúncios criados")
                .register(meterRegistry);
    }

    @Transactional
    @Observed(name = "property.create-listing")
    public Property create(String title, String city, BigDecimal monthlyRent) {
        Property property = repository.save(new Property(title, city, monthlyRent));
        createdListings.increment();
        return property;
    }

    @Transactional(readOnly = true)
    public List<Property> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public PropertySummary getById(Long id) {
        Property property = repository.findById(id)
                .orElseThrow(() -> new PropertyNotFoundException(id));
        return toSummary(property);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropertySummary> findAvailable(String city, BigDecimal maxMonthlyRent) {
        List<Property> properties = (city == null || city.isBlank())
                ? repository.findByStatusAndMonthlyRentLessThanEqual(ListingStatus.ACTIVE, maxMonthlyRent)
                : repository.findByCityAndStatusAndMonthlyRentLessThanEqual(
                        city, ListingStatus.ACTIVE, maxMonthlyRent);
        return properties.stream()
                .map(PropertyService::toSummary)
                .toList();
    }

    private static PropertySummary toSummary(Property property) {
        return new PropertySummary(
                property.getId(), property.getTitle(), property.getCity(), property.getMonthlyRent());
    }
}
```

O `VisitService` recebe o `MeterRegistry` e incrementa `visit.scheduled`:

```java
package com.example.propertyplatform.visit;

import java.util.List;

import com.example.propertyplatform.listing.PropertyCatalog;
import com.example.propertyplatform.visit.events.VisitScheduled;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitService {

    private final VisitRepository repository;

    private final PropertyCatalog propertyCatalog;

    private final ApplicationEventPublisher eventPublisher;

    private final Counter scheduledVisits;

    public VisitService(
            VisitRepository repository,
            PropertyCatalog propertyCatalog,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.propertyCatalog = propertyCatalog;
        this.eventPublisher = eventPublisher;
        this.scheduledVisits = Counter.builder("visit.scheduled")
                .description("Visitas agendadas")
                .register(meterRegistry);
    }

    @Transactional
    public Visit schedule(ScheduleVisitRequest request) {
        propertyCatalog.getById(request.propertyId());
        Visit visit = repository.save(new Visit(
                request.propertyId(), request.visitorName(), request.scheduledAt()));
        eventPublisher.publishEvent(new VisitScheduled(
                visit.getId(), visit.getPropertyId(), visit.getVisitorName(), visit.getScheduledAt()));
        scheduledVisits.increment();
        return visit;
    }

    @Transactional(readOnly = true)
    public List<Visit> findAll() {
        return repository.findAll();
    }
}
```

O `MaintenanceRequestService` incrementa `maintenance.requests.opened`:

```java
package com.example.propertyplatform.maintenance;

import com.example.maintenancecontract.api.MaintenanceServiceGrpc;
import com.example.maintenancecontract.api.OpenServiceOrderRequest;
import com.example.maintenancecontract.api.ServiceOrder;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceRequestService {

    private final MaintenanceRequestRepository repository;

    private final MaintenanceServiceGrpc.MaintenanceServiceBlockingStub maintenanceService;

    private final Counter openedRequests;

    public MaintenanceRequestService(
            MaintenanceRequestRepository repository,
            MaintenanceServiceGrpc.MaintenanceServiceBlockingStub maintenanceService,
            MeterRegistry meterRegistry) {
        this.repository = repository;
        this.maintenanceService = maintenanceService;
        this.openedRequests = Counter.builder("maintenance.requests.opened")
                .description("Pedidos de manutenção abertos")
                .register(meterRegistry);
    }

    @Transactional
    public MaintenanceRequest open(OpenMaintenanceRequest request) {
        ServiceOrder order = maintenanceService.openServiceOrder(OpenServiceOrderRequest.newBuilder()
                .setPropertyId(String.valueOf(request.propertyId()))
                .setDescription(request.description())
                .setPriority(request.priority())
                .build());
        MaintenanceRequest maintenanceRequest = repository.save(new MaintenanceRequest(
                request.propertyId(), request.description(), request.priority(), order.getOrderId()));
        openedRequests.increment();
        return maintenanceRequest;
    }
}
```

### Health dos dois processos

O `property-platform` verifica o gRPC do `maintenance-service` com um `HealthIndicator`. Ordem desconhecida devolve `NOT_FOUND`, o que ainda prova que o serviço respondeu; só uma falha de conexão vira `DOWN`:

```java
package com.example.propertyplatform.maintenance;

import com.example.maintenancecontract.api.GetServiceOrderRequest;
import com.example.maintenancecontract.api.MaintenanceServiceGrpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class MaintenanceServiceHealthIndicator implements HealthIndicator {

    private final MaintenanceServiceGrpc.MaintenanceServiceBlockingStub maintenanceService;

    public MaintenanceServiceHealthIndicator(
            MaintenanceServiceGrpc.MaintenanceServiceBlockingStub maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @Override
    public Health health() {
        try {
            maintenanceService.getServiceOrder(GetServiceOrderRequest.newBuilder()
                    .setOrderId("health-check")
                    .build());
            return Health.up().withDetail("maintenance-service", "reachable").build();
        } catch (StatusRuntimeException exception) {
            if (exception.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Health.up().withDetail("maintenance-service", "reachable").build();
            }
            return Health.down(exception).build();
        }
    }
}
```

O pacote é `org.springframework.boot.health.contributor`, o atual do Boot 4 (aula 08). Com `show-details: always`, o componente `maintenanceService` aparece no JSON do `/actuator/health`. Com o `maintenance-service` parado, o agregado responde `503` e o componente fica `DOWN`; com ele de pé, tudo `UP`.

Cada linha de log carrega `[app,traceId,spanId]`. O trace de uma requisição que agenda visita atravessa o servlet, o `VisitService` e o publisher RabbitMQ, e continua no `maintenance-service` quando o listener consome a fila, porque a mensagem AMQP carrega o contexto de trace. No Grafana, o datasource Tempo mostra o trace inteiro e o Loki cruza o log pelo mesmo `traceId`.

### Teste

Com o stack LGTM de pé (`docker compose up -d`) e os dois processos, acrescente os requests ao `requests.http`:

```http
### Saúde
GET http://localhost:8080/actuator/health

### Métrica de anúncios
GET http://localhost:8080/actuator/metrics/property.listings.created
Authorization: Bearer {{agentToken}}

### Métrica de visitas
GET http://localhost:8080/actuator/metrics/visit.scheduled
Authorization: Bearer {{agentToken}}
```

Resultados esperados:

- `GET /actuator/health` responde `200` com `status: UP` e o componente `maintenanceService` em `UP` quando o gRPC está de pé. Com o `maintenance-service` parado, o agregado responde `503` e o componente fica `DOWN`.
- `GET /actuator/metrics/property.listings.created` mostra o contador incrementado a cada anúncio criado. O health é aberto; as demais rotas do Actuator exigem token.
- No Grafana (`http://localhost:3000`), o datasource Tempo lista o trace da requisição com os spans do servlet e do `property.create-listing`, e o Loki mostra o log pelo mesmo `traceId`. Encadeie vários `POST /api/properties` antes de olhar a métrica `property_create_listing_seconds_count` no Prometheus.

## Parte 7 — Deploy

Os dois processos empacotam como jar, container e Deployment. A configuração por ambiente vem de fora: o perfil `prod` ajusta o que muda e as variáveis de ambiente sobrescrevem os valores locais (aula 10).

### Perfis

O perfil `prod` do `property-platform` reduz a amostragem de traces a 10% do volume:

```yaml
# property-platform/src/main/resources/application-prod.yaml
spring:
  config:
    activate:
      on-profile: prod
management:
  tracing:
    sampling:
      probability: 0.1
```

O do `maintenance-service`:

```yaml
# maintenance-service/src/main/resources/application-prod.yaml
spring:
  config:
    activate:
      on-profile: prod
management:
  tracing:
    sampling:
      probability: 0.1
```

Os demais valores entram por variável de ambiente no compose: `JWT_SECRET`, `SPRING_RABBITMQ_HOST`, `APP_MAINTENANCE_GRPC_TARGET`, `SPRING_AI_OLLAMA_BASE_URL` e as `OTLP_*`. O Spring faz o binding relaxado: `APP_MAINTENANCE_GRPC_TARGET` vira `app.maintenance.grpc-target`. Nenhum segredo entra no jar.

### Container

Fixe o nome do jar em cada `build.gradle.kts` para o Dockerfile não depender do nome gerado:

```kotlin
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("property-platform.jar")
}
```

```kotlin
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("maintenance-service.jar")
}
```

`property-platform/Dockerfile`:

```dockerfile
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY build/libs/property-platform.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`maintenance-service/Dockerfile`, com o nome do seu jar:

```dockerfile
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY build/libs/maintenance-service.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Dentro de cada diretório, gere o jar e a imagem:

```bash
./gradlew bootJar
docker build -t property-platform:local .
```

```bash
./gradlew bootJar
docker build -t maintenance-service:local .
```

### Compose completo

O `docker-compose.yml` ganha os dois processos. Os containers usam o H2 em memória padrão, então cada subida começa com o banco limpo. O Ollama continua na máquina host; o `extra_hosts` deixa os containers enxergarem `host.docker.internal`:

```yaml
services:
  rabbitmq:
    image: rabbitmq:4-management
    ports:
      - "5672:5672"
      - "15672:15672"

  otel-lgtm:
    image: grafana/otel-lgtm:latest
    ports:
      - "3000:3000"
      - "4317:4317"
      - "4318:4318"

  maintenance-service:
    image: maintenance-service:local
    extra_hosts:
      - "host.docker.internal:host-gateway"
    ports:
      - "9090:9090"
      - "8081:8081"
    depends_on:
      - rabbitmq
      - otel-lgtm
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_RABBITMQ_HOST: rabbitmq
      OTLP_METRICS_URL: http://otel-lgtm:4318/v1/metrics
      OTLP_TRACES_URL: http://otel-lgtm:4318/v1/traces
      OTLP_LOGS_URL: http://otel-lgtm:4318/v1/logs

  property-platform:
    image: property-platform:local
    extra_hosts:
      - "host.docker.internal:host-gateway"
    ports:
      - "8080:8080"
    depends_on:
      - rabbitmq
      - otel-lgtm
      - maintenance-service
    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_RABBITMQ_HOST: rabbitmq
      APP_MAINTENANCE_GRPC_TARGET: maintenance-service:9090
      SPRING_AI_OLLAMA_BASE_URL: http://host.docker.internal:11434
      JWT_SECRET: ${JWT_SECRET:dev-secret-com-pelo-menos-32-caracteres}
      OTLP_METRICS_URL: http://otel-lgtm:4318/v1/metrics
      OTLP_TRACES_URL: http://otel-lgtm:4318/v1/traces
      OTLP_LOGS_URL: http://otel-lgtm:4318/v1/logs
```

Suba tudo com `docker compose up -d`. A API fica em `8080`, o gRPC em `9090`, a gestão do `maintenance-service` em `8081`, o RabbitMQ em `15672` e o Grafana em `3000`.

### Kubernetes

O Actuator já publica os probes que o Kubernetes consome. O `Deployment` do `property-platform` aponta liveness e readiness para os endpoints e injeta os segredos por `secretKeyRef` (aula 10):

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: property-platform
spec:
  replicas: 2
  selector:
    matchLabels:
      app: property-platform
  template:
    metadata:
      labels:
        app: property-platform
    spec:
      containers:
        - name: property-platform
          image: registry.example.com/property-platform:latest
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: prod
            - name: JWT_SECRET
              valueFrom:
                secretKeyRef:
                  name: property-platform-secrets
                  key: jwt-secret
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
```

O `maintenance-service` usa o mesmo desenho, com `containerPort` `9090` e as probes na porta `8081`. A liveness reinicia o pod quando o processo trava; a readiness tira o pod do tráfego enquanto ele não está pronto. O Kubernetes não lê o `application.yaml`; ele só chama os endpoints configurados no manifest.

### Teste

Gere os jars e as imagens dentro de cada diretório (`property-platform` e `maintenance-service`):

```bash
./gradlew bootJar
docker build -t property-platform:local .
```

```bash
./gradlew bootJar
docker build -t maintenance-service:local .
```

Suba a stack na pasta do `docker-compose.yml` e confira os endpoints:

```bash
docker compose up -d
```

```http
### Saúde do property-platform
GET http://localhost:8080/actuator/health

### Saúde do maintenance-service
GET http://localhost:8081/actuator/health
```

Resultados esperados:

- Os dois healths respondem `200` com `status: UP`; o do `property-platform` tem o componente `maintenanceService` em `UP`, apontando para `maintenance-service:9090` via `APP_MAINTENANCE_GRPC_TARGET`.
- A API em `8080` responde igual à execução local, e o Grafana em `3000` recebe os traces dos containers.
- O gRPC do `maintenance-service` fica acessível em `9090`, com o reflection ligado.

## Estrutura

```
maintenance-contract/
├── build.gradle.kts
├── settings.gradle.kts
└── src/main/proto/maintenance.proto

property-platform/
├── build.gradle.kts
├── Dockerfile
├── k8s/deployment.yaml
└── src/
    ├── main/java/com/example/propertyplatform/
    │   ├── PropertyPlatformApplication.java
    │   ├── config/
    │   │   ├── JpaAuditingConfiguration.java
    │   │   └── RabbitMqTopologyConfiguration.java
    │   ├── web/ApiExceptionHandler.java
    │   ├── security/
    │   │   ├── SecurityProperties.java
    │   │   ├── UserStoreConfiguration.java
    │   │   ├── SecurityConfiguration.java
    │   │   ├── TokenService.java
    │   │   └── AuthController.java
    │   ├── listing/
    │   │   ├── Property.java
    │   │   ├── ListingStatus.java
    │   │   ├── PropertyRepository.java
    │   │   ├── PropertyCatalog.java
    │   │   ├── PropertySummary.java
    │   │   ├── PropertyService.java
    │   │   ├── CreatePropertyRequest.java
    │   │   ├── PropertyResponse.java
    │   │   ├── PropertyNotFoundException.java
    │   │   └── PropertyController.java
    │   ├── visit/
    │   │   ├── Visit.java
    │   │   ├── VisitStatus.java
    │   │   ├── VisitRepository.java
    │   │   ├── VisitService.java
    │   │   ├── ScheduleVisitRequest.java
    │   │   ├── VisitResponse.java
    │   │   ├── VisitController.java
    │   │   ├── VisitScheduledRabbitPublisher.java
    │   │   └── events/
    │   │       ├── VisitScheduled.java
    │   │       └── package-info.java
    │   ├── notification/
    │   │   ├── VisitNotificationListener.java
    │   │   └── package-info.java
    │   ├── maintenance/
    │   │   ├── MaintenanceRequest.java
    │   │   ├── MaintenanceRequestStatus.java
    │   │   ├── MaintenanceRequestRepository.java
    │   │   ├── MaintenanceGrpcClientConfiguration.java
    │   │   ├── OpenMaintenanceRequest.java
    │   │   ├── MaintenanceResponse.java
    │   │   ├── MaintenanceRequestService.java
    │   │   ├── MaintenanceController.java
    │   │   └── MaintenanceServiceHealthIndicator.java
    │   ├── assistant/
    │   │   ├── AssistantConfiguration.java
    │   │   ├── PropertyTools.java
    │   │   ├── KnowledgeConfiguration.java
    │   │   ├── PolicyIndexer.java
    │   │   ├── KnowledgeMatch.java
    │   │   ├── KnowledgeService.java
    │   │   ├── AssistantService.java
    │   │   └── AssistantController.java
    │   └── reporting/
    │       ├── MonthlyVisitRow.java
    │       ├── MonthlyReportLine.java
    │       ├── MonthlyReportProcessor.java
    │       ├── MonthlyReportWriter.java
    │       ├── MonthlyReportJobConfiguration.java
    │       ├── MonthlyReportRunner.java
    │       └── MonthlyReportController.java
    ├── main/resources/
    │   ├── application.yaml
    │   ├── application-prod.yaml
    │   ├── schema.sql
    │   └── requests.http
    └── test/java/com/example/propertyplatform/ArchitectureTest.java

maintenance-service/
├── build.gradle.kts
├── Dockerfile
└── src/
    ├── main/java/com/example/maintenanceservice/
    │   ├── MaintenanceServiceApplication.java
    │   ├── config/AmqpConfiguration.java
    │   ├── orders/
    │   │   ├── WorkOrder.java
    │   │   ├── WorkOrderStatus.java
    │   │   ├── WorkOrderRepository.java
    │   │   └── WorkOrderService.java
    │   ├── grpc/MaintenanceGrpcEndpoint.java
    │   └── messaging/
    │       ├── VisitScheduledMessage.java
    │       └── VisitScheduledListener.java
    └── main/resources/
        ├── application.yaml
        ├── application-prod.yaml
        └── requests.http

docker-compose.yml
```

## Fechando

A plataforma amarra os tópicos no ponto do fluxo em que cada um resolve um problema: REST, JPA e Security no anúncio; evento de domínio, Modulith e RabbitMQ na visita; gRPC na manutenção; Spring AI no assistente; Spring Batch no relatório; Actuator e OTLP na operação; perfis, container e Kubernetes no deploy.

O schema desta aula fica com o Hibernate (`ddl-auto: update`). Em produção, migre para Flyway versionado com `ddl-auto: validate` (aula 04), incluindo as tabelas de metadata do Batch e do Modulith. O `SimpleVectorStore` e a memória do assistente são de processo; troque por banco vetorial e `ChatMemory` persistente quando a conversa precisar sobreviver (aula 14). A fila `visit-scheduled.dlq` guarda o que falhar no consumo; monitore a profundidade dela.
