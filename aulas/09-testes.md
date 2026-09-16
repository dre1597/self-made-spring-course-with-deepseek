# Aula 09 — Testes

Objetivo: testar uma aplicação Spring em níveis diferentes, usando JUnit 6, AssertJ, `@SpringBootTest`, slices, segurança, eventos e Testcontainers.

Domínio: reservas de cinema. A aplicação cadastra filmes, consulta sessões e cria ou cancela reservas. O domínio é pequeno de propósito: cada teste mostra uma decisão de teste, sem esconder a ideia atrás de regra de negócio demais.

## Projeto da aula

Esta aula usa um projeto próprio, separado das outras aulas. O código fica no pacote `com.example.cinema`.

As dependências específicas desta aula entram no projeto-base:

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")

    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}
```

## JUnit 6 e AssertJ

O `spring-boot-starter-test` traz JUnit 6, AssertJ e Mockito. Os starters de teste dos slices trazem o suporte específico de cada fatia. O starter de segurança testa filtros e usuários simulados. O `spring-boot-testcontainers` integra `@ServiceConnection` ao Boot; `testcontainers-junit-jupiter` integra o ciclo de vida do container ao JUnit; `testcontainers-postgresql` fornece o módulo específico do banco.

O Boot 4 usa JUnit 6. A migração de um teste escrito com JUnit 5 costuma ser pequena: os imports de `org.junit.jupiter.api` continuam iguais, assim como os asserts do AssertJ. O projeto-base já configura o Gradle para executar a plataforma Jupiter.

O projeto começa com uma aplicação vazia:

```java
package com.example.cinema;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CinemaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CinemaApplication.class, args);
    }
}
```

O H2 serve para os testes locais. A configuração mínima fica em `src/test/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:cinema
  jpa:
    hibernate:
      ddl-auto: create-drop
```

## Antes de testar

Um teste precisa deixar claro qual fronteira ele protege. Uma regra de negócio pode rodar sem Spring; uma query precisa de persistência; um endpoint precisa da camada MVC; um fluxo entre beans precisa do contexto da aplicação.

Esta aula percorre essas fronteiras nesta ordem:

- `@SpringBootTest` prova a integração do serviço com os repositories e o banco.
- `@WebMvcTest` isola o controller e verifica a resposta HTTP com colaboradores simulados.
- `@DataJpaTest` isola a persistência e verifica a query com banco de teste.
- o teste de segurança verifica autenticação e autorização no endpoint protegido.
- o teste de eventos verifica o efeito publicado pelo serviço.
- Testcontainers repete um teste de persistência contra PostgreSQL, o banco mais próximo do ambiente real.

O mesmo domínio aparece em todas as seções, mas cada teste tem uma pergunta diferente. Você não escolhe a anotação pelo tamanho da classe; escolhe pela fronteira que quer garantir.

## Modelo usado pelos testes

O filme é uma entidade simples:

```java
package com.example.cinema.movie;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "movies")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    protected Movie() {
    }

    public Movie(String title) {
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }
}
```

Uma sessão pertence a um filme e informa quando ela começa:

```java
package com.example.cinema.screening;

import java.time.Instant;

import com.example.cinema.movie.Movie;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "screenings")
public class Screening {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Movie movie;

    private Instant startsAt;

    protected Screening() {
    }

    public Screening(Movie movie, Instant startsAt) {
        this.movie = movie;
        this.startsAt = startsAt;
    }

    public Long getId() {
        return id;
    }

    public Movie getMovie() {
        return movie;
    }

    public Instant getStartsAt() {
        return startsAt;
    }
}
```

O repositório consulta as sessões de um filme:

```java
package com.example.cinema.screening;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

    List<Screening> findByMovieId(Long movieId);
}
```

Uma reserva guarda o nome de quem comprou e a sessão escolhida:

```java
package com.example.cinema.booking;

import com.example.cinema.screening.Screening;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Screening screening;

    private String customerName;

    protected Booking() {
    }

    public Booking(Screening screening, String customerName) {
        this.screening = screening;
        this.customerName = customerName;
    }

    public Long getId() {
        return id;
    }

    public Screening getScreening() {
        return screening;
    }

    public String getCustomerName() {
        return customerName;
    }
}
```

O serviço cria a reserva e publica um evento. A publicação permite testar duas coisas separadamente: o resultado da operação e o efeito colateral que ela produz.

```java
package com.example.cinema.booking;

import com.example.cinema.screening.Screening;
import com.example.cinema.screening.ScreeningRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ScreeningRepository screeningRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BookingService(BookingRepository bookingRepository,
                          ScreeningRepository screeningRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.screeningRepository = screeningRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Booking create(CreateBookingRequest request) {
        Screening screening = screeningRepository.findById(request.screeningId())
                .orElseThrow(() -> new IllegalArgumentException("Sessão não encontrada"));
        Booking booking = bookingRepository.save(new Booking(screening, request.customerName()));
        eventPublisher.publishEvent(new BookingCreated(booking.getId(), screening.getId()));
        return booking;
    }

    public record CreateBookingRequest(Long screeningId, String customerName) {
    }
}
```

Os tipos auxiliares do serviço são estes:

```java
package com.example.cinema.booking;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
}
```

```java
package com.example.cinema.booking;

public record BookingCreated(Long bookingId, Long screeningId) {
}
```

O controller expõe consulta, criação e cancelamento. A implementação do cancelamento fica simples porque o objetivo da seção de segurança é testar autorização do endpoint:

```java
package com.example.cinema.booking;

import java.util.List;

import com.example.cinema.screening.Screening;
import com.example.cinema.screening.ScreeningRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CinemaController {

    private final ScreeningRepository screeningRepository;
    private final BookingService bookingService;
    private final BookingRepository bookingRepository;

    public CinemaController(ScreeningRepository screeningRepository,
                            BookingService bookingService,
                            BookingRepository bookingRepository) {
        this.screeningRepository = screeningRepository;
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
    }

    @GetMapping("/movies/{movieId}/screenings")
    public List<Screening> screenings(@PathVariable Long movieId) {
        return screeningRepository.findByMovieId(movieId);
    }

    @PostMapping("/bookings")
    public ResponseEntity<Booking> create(@RequestBody BookingService.CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
    }

    @DeleteMapping("/bookings/{bookingId}")
    public ResponseEntity<Void> cancel(@PathVariable Long bookingId) {
        bookingRepository.deleteById(bookingId);
        return ResponseEntity.noContent().build();
    }
}
```

## `@SpringBootTest`

`@SpringBootTest` sobe o contexto completo. O teste atravessa controller, serviço, repositório e banco quando você usa o endpoint; para testar o serviço diretamente, ainda assim o Spring monta todos os beans da aplicação.

Neste primeiro teste, a reserva precisa encontrar uma sessão persistida, gravar a reserva e retornar um id:

```java
package com.example.cinema.booking;

import java.time.Instant;

import com.example.cinema.movie.Movie;
import com.example.cinema.movie.MovieRepository;
import com.example.cinema.screening.Screening;
import com.example.cinema.screening.ScreeningRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookingServiceTest {

    @Autowired
    BookingService bookingService;

    @Autowired
    MovieRepository movieRepository;

    @Autowired
    ScreeningRepository screeningRepository;

    @Test
    void createsBookingForExistingScreening() {
        Movie movie = movieRepository.save(new Movie("Duna: Parte Dois"));
        Screening screening = screeningRepository.save(
                new Screening(movie, Instant.parse("2026-10-10T20:00:00Z")));

        Booking booking = bookingService.create(
                new BookingService.CreateBookingRequest(screening.getId(), "Ana"));

        assertThat(booking.getId()).isNotNull();
        assertThat(booking.getCustomerName()).isEqualTo("Ana");
    }
}
```

O exemplo usa `MovieRepository`, então ele precisa existir no projeto:

```java
package com.example.cinema.movie;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<Movie, Long> {
}
```

Use o contexto completo quando a integração entre componentes for o que você quer provar. Ele custa mais tempo e torna a falha menos localizada. Um teste que verifica uma regra pura não precisa de `@SpringBootTest`.

## `@WebMvcTest`

`@WebMvcTest` carrega a infraestrutura MVC e o controller indicado, mas não sobe repositórios, banco ou serviços reais. As dependências do controller entram como `@MockitoBean`.

O teste de controller consulta sessões. O serviço não participa do cenário, então o repository vira mock:

```java
package com.example.cinema.screening;

import java.time.Instant;
import java.util.List;

import com.example.cinema.booking.BookingRepository;
import com.example.cinema.booking.BookingService;
import com.example.cinema.config.SecurityConfiguration;
import com.example.cinema.movie.Movie;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest
@Import(SecurityConfiguration.class)
class CinemaControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    ScreeningRepository screeningRepository;

    @MockitoBean
    BookingService bookingService;

    @MockitoBean
    BookingRepository bookingRepository;

    @Test
    void listsScreeningsForMovie() {
        Movie movie = new Movie("Ainda Estou Aqui");
        Screening screening = new Screening(movie, Instant.parse("2026-10-11T18:00:00Z"));
        Mockito.when(screeningRepository.findByMovieId(7L)).thenReturn(List.of(screening));

        assertThat(mvc.get().uri("/api/movies/7/screenings"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[0].movie.title")
                .isEqualTo("Ainda Estou Aqui");
    }
}
```

Neste caso o `@WebMvcTest` sem classe explícita encontra o controller da aplicação. Usar `@WebMvcTest(CinemaController.class)` deixa o recorte mais claro quando o projeto tem vários controllers.

No Boot 4, `@WebMvcTest` fica em `org.springframework.boot.webmvc.test.autoconfigure`. O antigo `@MockBean` foi substituído por `@MockitoBean`, de `org.springframework.test.context.bean.override.mockito`. O `MockMvcTester` usa AssertJ para verificar a resposta, em vez da API antiga baseada em matchers.

## `@DataJpaTest`

`@DataJpaTest` carrega entidades, repositories e a configuração de persistência. O teste usa o H2 definido para a aula e cada método roda com rollback automático.

O repository recebe um filme e duas sessões, depois filtra pelo id do filme:

```java
package com.example.cinema.screening;

import java.time.Instant;

import com.example.cinema.movie.Movie;
import com.example.cinema.movie.MovieRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ScreeningRepositoryTest {

    @Autowired
    ScreeningRepository screeningRepository;

    @Autowired
    MovieRepository movieRepository;

    @Test
    void findsScreeningsByMovie() {
        Movie movie = movieRepository.save(new Movie("Oppenheimer"));
        screeningRepository.save(new Screening(movie, Instant.parse("2026-10-12T18:00:00Z")));
        screeningRepository.save(new Screening(movie, Instant.parse("2026-10-12T21:00:00Z")));

        assertThat(screeningRepository.findByMovieId(movie.getId())).hasSize(2);
    }
}
```

O slice não é um contexto incompleto por acidente. Ele é um recorte intencional: o teste prova o mapeamento JPA e a consulta sem pagar o custo da aplicação inteira. `@DataJpaTest` fica em `org.springframework.boot.data.jpa.test.autoconfigure` no Boot 4.

## Segurança

O endpoint de cancelamento exige o papel `STAFF`. A aplicação usa uma configuração pequena e desliga CSRF porque esta API trabalha com autenticação stateless; o teste não precisa enviar token CSRF para uma operação de API.

```java
package com.example.cinema.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/movies/**", "/api/bookings").permitAll()
                        .requestMatchers("/api/bookings/**").hasRole("STAFF")
                        .anyRequest().authenticated())
                .httpBasic(httpBasic -> {
                });
        return http.build();
    }
}
```

`@WithMockUser` coloca um usuário simulado no `SecurityContext` antes do método de teste. O teste autorizado pode cancelar; o teste sem usuário recebe `401`; um usuário autenticado sem o papel recebe `403`:

```java
package com.example.cinema.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.example.cinema.config.SecurityConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(CinemaController.class)
@Import(SecurityConfiguration.class)
class BookingControllerSecurityTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    BookingService bookingService;

    @MockitoBean
    BookingRepository bookingRepository;

    @MockitoBean
    com.example.cinema.screening.ScreeningRepository screeningRepository;

    @Test
    @WithMockUser(roles = "STAFF")
    void staffCanCancelBooking() {
        assertThat(mvc.delete().uri("/api/bookings/1")).hasStatus(204);
    }

    @Test
    void anonymousUserCannotCancelBooking() {
        assertThat(mvc.delete().uri("/api/bookings/1")).hasStatus(401);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotCancelBooking() {
        assertThat(mvc.delete().uri("/api/bookings/1")).hasStatus(403);
    }
}
```

O teste de segurança deve verificar a política da aplicação, não a implementação interna do filtro. `@WithMockUser` não prova que um JWT real foi validado; ele prova que a autorização do endpoint reage ao usuário e ao papel esperados. O teste de autenticação com o provedor real pertence a outra fronteira de integração.

## Eventos

O serviço publica `BookingCreated` depois de salvar a reserva. `@RecordApplicationEvents` registra os eventos publicados durante o teste para que você consulte o tipo e o conteúdo.

```java
package com.example.cinema.booking;

import java.time.Instant;

import com.example.cinema.movie.Movie;
import com.example.cinema.movie.MovieRepository;
import com.example.cinema.screening.Screening;
import com.example.cinema.screening.ScreeningRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RecordApplicationEvents
class BookingServiceEventsTest {

    @Autowired
    BookingService bookingService;

    @Autowired
    MovieRepository movieRepository;

    @Autowired
    ScreeningRepository screeningRepository;

    @Autowired
    ApplicationEvents events;

    @Test
    void publishesBookingCreated() {
        Movie movie = movieRepository.save(new Movie("O Auto da Compadecida"));
        Screening screening = screeningRepository.save(
                new Screening(movie, Instant.parse("2026-10-13T19:00:00Z")));

        bookingService.create(new BookingService.CreateBookingRequest(screening.getId(), "Bia"));

        assertThat(events.stream(BookingCreated.class))
                .singleElement()
                .satisfies(event -> assertThat(event.screeningId()).isEqualTo(screening.getId()));
    }
}
```

`@RecordApplicationEvents` captura eventos publicados pelo contexto durante o teste. Ele não espera um listener assíncrono terminar e não testa entrega por Kafka, RabbitMQ ou JMS. Para esses casos, você precisa testar o broker ou o adaptador que publica nele.

## Testcontainers com PostgreSQL

H2 ajuda a testar rápido, mas não implementa todos os detalhes do PostgreSQL. Neste teste não existe um PostgreSQL instalado na máquina nem um servidor iniciado pelo projeto. O Testcontainers pede ao Docker um container criado a partir da imagem `postgres:17`. Se a imagem ainda não estiver disponível, o Docker baixa; depois inicia um PostgreSQL temporário, com porta, banco, usuário e senha próprios daquele container.

O `@ServiceConnection` lê os dados de conexão do container e entrega tudo ao Spring Boot. Por isso o teste não precisa de URL, porta ou credenciais fixas em `application.yaml`. Ao fim da classe, o Testcontainers para e remove o container. O único requisito externo é o Docker estar instalado e em execução.

Docker Compose não é necessário neste caso. Ele seria útil se o teste precisasse iniciar uma topologia com PostgreSQL, RabbitMQ e outros serviços ao mesmo tempo. Para uma classe que testa o repositório contra um banco real, o container declarado no próprio teste é menor, isolado e começa sempre de um estado conhecido.

```java
package com.example.cinema.screening;

import java.time.Instant;

import com.example.cinema.movie.Movie;
import com.example.cinema.movie.MovieRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class ScreeningRepositoryPostgresTest {

    @Container
    @ServiceConnection
    static var postgres = new PostgreSQLContainer("postgres:17");

    @Autowired
    ScreeningRepository screeningRepository;

    @Autowired
    MovieRepository movieRepository;

    @Test
    void savesScreeningInPostgres() {
        Movie movie = movieRepository.save(new Movie("Central do Brasil"));
        screeningRepository.save(
                new Screening(movie, Instant.parse("2026-10-14T20:00:00Z")));

        assertThat(screeningRepository.findByMovieId(movie.getId())).hasSize(1);
    }
}
```

O Docker precisa estar disponível para este teste. Os outros testes continuam usando H2 e não dependem do container. Execute a suíte completa com:

```bash
./gradlew test
```

Para executar só o teste que usa PostgreSQL:

```bash
./gradlew test --tests '*ScreeningRepositoryPostgresTest'
```

Se o container falhar antes de o teste começar, confira o daemon do Docker e a imagem `postgres:17`. Esse tipo de falha é infraestrutura de teste, não uma falha da asserção.

## Estrutura

```text
src/main/java/com/example/cinema/
├── CinemaApplication.java
├── booking/
│   ├── Booking.java
│   ├── BookingCreated.java
│   ├── BookingRepository.java
│   ├── BookingService.java
│   └── CinemaController.java
├── config/
│   └── SecurityConfiguration.java
├── movie/
│   ├── Movie.java
│   └── MovieRepository.java
└── screening/
    ├── Screening.java
    └── ScreeningRepository.java
src/test/java/com/example/cinema/
├── booking/
│   ├── BookingControllerSecurityTest.java
│   ├── BookingServiceEventsTest.java
│   └── BookingServiceTest.java
└── screening/
    ├── ScreeningRepositoryPostgresTest.java
    ├── ScreeningRepositoryTest.java
    └── CinemaControllerTest.java
src/test/resources/
└── application.yaml
```

Os testes de `CinemaController` ficam no pacote `screening` porque exercitam a consulta de sessões, mas a classe testada pertence ao pacote `booking`. Em um projeto real, mantenha os testes próximos da fronteira que eles verificam e ajuste o nome do diretório ao padrão que você escolher.
