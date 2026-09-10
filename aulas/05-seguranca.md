# Aula 05 — Segurança

Objetivo: proteger a API com Spring Security 7, autenticação JWT stateless e MFA nativo.

## CSRF e o que mudou no Security 7

Base do projeto:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-security")
```

O `webmvc` é a base da API; o `starter-security` liga a proteção assim que entra no classpath.

A classe main do `tasks-api`, padrão de toda aula:

```java
package com.example.tasks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TasksApplication {

    public static void main(String[] args) {
        SpringApplication.run(TasksApplication.class, args);
    }
}
```

Sem nenhuma `SecurityFilterChain` definida, o Boot aplica um default com `httpBasic` e `formLogin`. Assim que o projeto define a própria chain, esse default sai fora: a chain é 100% sua, e o que não for configurado nela não existe. Numa API com Basic, o `httpBasic(...)` precisa ser chamado na chain — sem ele, o entry point vira `Http403ForbiddenEntryPoint` e a resposta é 403 sem `WWW-Authenticate`. Aqui a autenticação é JWT.

CSRF vem ligado por default, e o Security 7 mudou o DSL:

- `and()` sumiu; cada seção usa lambda.
- `authorizeRequests` virou `authorizeHttpRequests`.
- A configuração é modular, via `SecurityFilterChain`.

CSRF protege formulários de navegador contra envio forjado. Numa API stateless com JWT, o token via no header `Authorization`, não em cookie que o navegador envia sozinho. Então CSRF não protege nada aqui e se desliga.

## Autenticação JWT stateless

O fluxo completo (login, token, rota protegida) roda no mesmo app: um endpoint de login autentica usuário/senha e devolve o JWT; o resource server valida o token nas demais rotas. Em produção a emissão costuma ser um serviço separado, mas o desenho é o mesmo.

Dependência da seção:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
```

O starter traz o suporte a JWT do resource server e o `spring-security-oauth2-jose`, que fornece encoder e decoder.

Usuários em memória, com hash BCrypt, vindos do `application.yaml`:

```java
package com.example.tasks.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record AppUsersProperties(List<User> users) {

    public record User(String username, String password, List<String> roles) {
    }
}
```

```java
package com.example.tasks.config;

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
@EnableConfigurationProperties(AppUsersProperties.class)
public class UsersConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AppUsersProperties properties) {
        return new InMemoryUserDetailsManager(properties.users().stream()
                .map(this::toUserDetails)
                .toList());
    }

    private UserDetails toUserDetails(AppUsersProperties.User user) {
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

No Security 7 o `DaoAuthenticationProvider` recebe o `UserDetailsService` no construtor — o construtor sem-args e o `setUserDetailsService` foram removidos. O `AuthenticationManager` autentica a credencial contra o `UserDetailsService`, comparando com o `PasswordEncoder`. Os hashes (de `password`, dos dois users) ficam no `application.yaml`, junto com o `jwt-secret`.

Configuração principal, o resource server:

```java
package com.example.tasks.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Profile("!oidc")
    JwtDecoder jwtDecoder(@Value("${app.security.jwt-secret}") String secret) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
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

Ponto a ponto:

- `csrf(AbstractHttpConfigurer::disable)`: desliga CSRF, inútil em API stateless com JWT no header.
- `cors(Customizer.withDefaults())`: aplica o `CorsConfigurationSource` da seção CORS.
- `authorizeHttpRequests`: `/api/auth/**` aberto (é o login), o resto exige autenticação.
- `oauth2ResourceServer(...)`: a API passa a aceitar `Authorization: Bearer <token>`. O converter lê a claim `roles` do token e vira authority (`ROLE_ADMIN`, `ROLE_USER`) — sem isso, `hasRole(...)` nunca funciona, porque o default só olha a claim `scope`.
- `sessionManagement(...STATELESS)`: nada de sessão; cada requisição se autentica pelo token.
- `jwtDecoder`: valida assinatura HMAC. O segredo e os users de teste vêm do `application.yaml`:

```yaml
app:
  security:
    jwt-secret: ${JWT_SECRET:dev-secret-com-pelo-menos-32-caracteres}
    users:
      - username: user
        password: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
        roles:
          - USER
      - username: admin
        password: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG
        roles:
          - ADMIN
```

Sem a variável `JWT_SECRET`, vale o default de dev (chave com tamanho suficiente pro HMAC-SHA256). Em produção, `JWT_SECRET` sai do ambiente. Os hashes são de `password`; gere o seu com `encoder.encode(...)` e troque no yaml se quiser.

Emissão: o `NimbusJwtEncoder` assina o JWT com a mesma chave:

```java
package com.example.tasks.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

@Configuration
public class TokenEncodingConfiguration {

    @Bean
    JwtEncoder jwtEncoder(@Value("${app.security.jwt-secret}") String secret) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }
}
```

O serviço monta as claims:

```java
package com.example.tasks.auth;

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
                .issuer("tasks-api")
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

O `header` fixa o algoritmo de assinatura (`HS256`). Sem ele, o `NimbusJwtEncoder` não seleciona a chave `oct` do `ImmutableSecret` e falha com `Failed to select a JWK signing key` na hora de assinar.

O login expõe o token:

```java
package com.example.tasks.auth;

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

O `authenticationManager.authenticate(...)` valida a credencial; se a senha não bater, lança `BadCredentialsException` e o `POST /api/auth/login` responde 401. O token volta no corpo, e as próximas chamadas mandam `Authorization: Bearer <token>`.

A claim `roles` carrega o nome da role **sem** o prefixo `ROLE_` (o `roleName` tira do authority `ROLE_ADMIN` → `ADMIN`), porque o converter do resource server adiciona o prefixo de novo. Se a claim vier com prefixo, vira `ROLE_ROLE_ADMIN` e o `hasRole('ADMIN')` falha com 403 `insufficient_scope`.

Pra OAuth2/OpenID Connect (OIDC) completo (clientes, refresh token, consentimento, logout), use o Spring Authorization Server, `spring-boot-starter-oauth2-authorization-server`. Ele traz a infra inteira; o encoder manual cobre o caso onde você só precisa assinar um token.

## Password encoding

Senha nunca vai pro banco em texto; o `PasswordEncoder` faz o hash. O bean `BCryptPasswordEncoder` do `UsersConfiguration` é o que o `DaoAuthenticationProvider` usa pra autenticar: ele compara a senha digitada com o hash guardado via `matches(...)`.

```java
var encoder = new BCryptPasswordEncoder();
String hash = encoder.encode("password");
boolean confere = encoder.matches("password", hash);
```

BCrypt é o padrão. Argon2 e scrypt são alternativas mais recentes. O `DelegatingPasswordEncoder` guarda o algoritmo no hash (`{bcrypt}...`) e permite migrar de algoritmo sem re-hash de tudo de uma vez.

## Rota protegida e autorização por método

Uma rota de exemplo pra testar o token. O `GET /api/tasks` só exige autenticação (qualquer token); o `DELETE /api/tasks/{id}` exige a role `ADMIN`:

```java
package com.example.tasks.task;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final Map<Long, String> tasks = new ConcurrentHashMap<>();

    public TaskController() {
        tasks.put(1L, "Escrever a aula 05");
        tasks.put(2L, "Testar o fluxo JWT");
    }

    @GetMapping
    public Map<Long, String> findAll() {
        return tasks;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id) {
        tasks.remove(id);
    }
}
```

Pra `@PreAuthorize` funcionar, liga o method security:

```java
package com.example.tasks.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity
public class MethodSecurityConfiguration {
}
```

A regra vale em qualquer chamada ao método, venha de controller, de serviço ou de evento. Expressions comuns: `hasRole('ADMIN')`, `hasAuthority('SCOPE_tasks.write')`, `isAuthenticated()`.

Teste do fluxo completo, `src/main/resources/requests.http`:

```http
### Login do user
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{ "username": "user", "password": "password" }

> {%
    client.global.set("userToken", response.body.token);
%}

### Login do admin
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{ "username": "admin", "password": "password" }

> {%
    client.global.set("adminToken", response.body.token);
%}

### GET /api/tasks com token do user
GET http://localhost:8080/api/tasks
Authorization: Bearer {{userToken}}

### GET /api/tasks com token do admin
GET http://localhost:8080/api/tasks
Authorization: Bearer {{adminToken}}

### DELETE com token do admin (200)
DELETE http://localhost:8080/api/tasks/1
Authorization: Bearer {{adminToken}}

### DELETE com token do user (403)
DELETE http://localhost:8080/api/tasks/1
Authorization: Bearer {{userToken}}

### GET sem token (401)
GET http://localhost:8080/api/tasks
```

Os blocos `> { % ... % }` após cada login salvam o token do corpo da resposta numa variável global (`client.global.set(...)`), e as demais chamadas usam `{{userToken}}`/`{{adminToken}}` — não precisa copiar token à mão, é só rodar o login pra renovar. O `DELETE` com token do `user` responde 403: o JWT tem a role `USER`, e o `@PreAuthorize("hasRole('ADMIN')")` barra. A role viaja na claim `roles` do token, que o converter do resource server transforma em authority (`ROLE_ADMIN`, `ROLE_USER`).

## CORS

CORS decide se um navegador numa origem pode chamar a API noutra origem. Com frontend separado, o navegador bloqueia a resposta sem o header de permissão. Configure as origens no filtro:

```java
package com.example.tasks.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CustomCorsConfiguration {

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:8081"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

O bean entra no filtro com `http.cors(Customizer.withDefaults())`, já ligado na `SecurityConfiguration`. Aponte a origem exata, nunca `*` com credencial junto. CORS protege o navegador, não a API: quem autoriza o acesso é o token, não o header.

Pra testar, um frontend em `http://localhost:8081` chamando a API: o navegador manda a preflight `OPTIONS` com `Origin` e `Access-Control-Request-Method`. No `requests.http`, a preflight fica assim:

```http
### Preflight de CORS
OPTIONS http://localhost:8080/api/tasks
Origin: http://localhost:8081
Access-Control-Request-Method: GET
```

Resposta esperada traz `Access-Control-Allow-Origin: http://localhost:8081`. Se a origem não estiver na lista, o header não aparece e o navegador bloqueia.

## MFA nativo

Cada fator de autenticação vira uma `FactorGrantedAuthority`. Autenticou com senha, ganha `PASSWORD_AUTHORITY`; completou one-time token, ganha `OTT_AUTHORITY`. A autorização então exige quantos fatores quiser. O `@EnableMultiFactorAuthentication` aplica a exigência a todas as regras.

MFA é app com navegador (form login + OTT), não API stateless — então vira um segundo projeto da aula, `mfa-demo`. As dependências dele:

```kotlin
implementation("org.springframework.boot:spring-boot-starter-webmvc")
implementation("org.springframework.boot:spring-boot-starter-security")
```

Configuração, `webmvc` + `security` e nada de JWT:

```java
package com.example.mfa.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authorization.EnableMultiFactorAuthentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMultiFactorAuthentication(authorities = {
        FactorGrantedAuthority.PASSWORD_AUTHORITY,
        FactorGrantedAuthority.OTT_AUTHORITY
})
public class MfaConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .oneTimeTokenLogin(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService() {
        UserDetails user = User.withUsername("user")
                .password("{noop}password")
                .roles("USER")
                .build();
        UserDetails admin = User.withUsername("admin")
                .password("{noop}password")
                .roles("ADMIN", "USER")
                .build();
        return new InMemoryUserDetailsManager(user, admin);
    }
}
```

Senha em texto (`{noop}`) é só do demo; em produção, o hash é o `BCryptPasswordEncoder` da seção Password encoding.

A classe main do `mfa-demo`:

```java
package com.example.mfa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MfaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MfaApplication.class, args);
    }
}
```

O `oneTimeTokenLogin(...)` expõe o endpoint `POST /ott/generate` (pra pedir o token) e a página default de submit em `GET /login/ott`. O token não tem como ser entregue sozinho — a entrega é sua (email, SMS, etc.). Pra teste, um handler que imprime o token no console e redireciona pra página de submit com o token no query param — a página default detecta o `token` na URL e já preenche o form:

```java
package com.example.mfa.config;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class ConsoleOneTimeTokenGenerationSuccessHandler implements OneTimeTokenGenerationSuccessHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, OneTimeToken oneTimeToken)
            throws IOException, ServletException {
        System.out.println("[OTT] token de " + oneTimeToken.getUsername() + ": " + oneTimeToken.getTokenValue());
        response.sendRedirect("/login/ott?token=" + oneTimeToken.getTokenValue());
    }
}
```

E as páginas do app:

```java
package com.example.mfa.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {

    @GetMapping("/")
    @ResponseBody
    public String home() {
        return "Página aberta";
    }

    @GetMapping("/admin")
    @ResponseBody
    public String admin() {
        return "Painel do admin";
    }
}
```

O `POST /ott/generate` que pede o token é um filtro do Spring Security registrado pelo `oneTimeTokenLogin(...)` — não é um controller seu, por isso não aparece no `/actuator/mappings`. Quem chama ele é o form "Send Token" da página de login; um `POST` direto de fora do navegador esbarra no CSRF (que fica ligado no app de navegador), então o teste é no navegador mesmo.

Teste no navegador (`mfa-demo` na porta 8081):

1. `server.port: 8081` no `application.yaml` do `mfa-demo` (o `tasks-api` fica na 8080).
2. Abra `http://localhost:8081/` (aberto) e `http://localhost:8081/admin`.
3. O `/admin` cai na página de login (`/login`), que tem dois forms: o de senha e o "Request a One-Time Token" (botão *Send Token*).
4. Entre com `admin`/`password` no form de senha. Ganha o fator senha, falta o OTT → o Security redireciona pro passo do token.
5. No form "Send Token", digite `admin` e clique. O `POST /ott/generate` roda, o token sai no console (`[OTT] token de admin: ...`) e você é redirecionado pra `http://localhost:8081/login/ott?token=...` — a página de submit já vem com o token preenchido.
6. Confirme o envio. Com os dois fatores, o `/admin` libera.

Se logar como `user` (role `USER`), mesmo com os dois fatores o `/admin` responde 403 — o MFA valida o *como* você autenticou, a role ainda decide o *o que* você pode acessar.

Pra exigir MFA só em parte do app, use `AuthorizationManagerFactories` no lugar da anotação global — a mesma regra de fatores, aplicada por rota:

```java
@Bean
AuthorizationManagerFactory<Object> mfa() {
    return AuthorizationManagerFactories.multiFactor()
            .requireFactors(
                    FactorGrantedAuthority.PASSWORD_AUTHORITY,
                    FactorGrantedAuthority.OTT_AUTHORITY)
            .build();
}

http.authorizeHttpRequests(authorize -> authorize
        .requestMatchers("/admin/**").access(mfa().hasRole("ADMIN"))
        .anyRequest().authenticated());
```

Numa API stateless, MFA por redirecionamento não existe — não tem página pra redirecionar. O segundo fator chega como outro token ou header, e a regra de fatores entra nas authorities do JWT.

## Spring Session

JWT stateless resolve a API. App de navegador (portal, backoffice) usa sessão, e aí vem o problema: o container guarda a sessão na memória do nó, então duas instâncias atrás de um load balancer derrubam o usuário a cada requisição que cai no nó errado. Spring Session move a sessão pra um store compartilhado.

No `mfa-demo`, sessão com JDBC (H2 em arquivo, pra teste local):

```kotlin
implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
runtimeOnly("com.h2database:h2")
```

O código continua usando `HttpSession`; o Boot troca a implementação por trás. Com JDBC a sessão vira linha na tabela `SPRING_SESSION`. O `springSessionRepositoryFilter` intercepta a requisição e substitui a sessão do container.

`application.yaml` do `mfa-demo`:

```yaml
server:
  port: 8081
  servlet:
    session:
      timeout: 30m
      cookie:
        http-only: true
        secure: false
        same-site: lax

spring:
  datasource:
    url: jdbc:h2:file:./data/session
    username: sa
    password: ""
  session:
    store-type: jdbc
```

`http-only` impede leitura por JavaScript, `secure` manda o cookie só por HTTPS, `same-site` bloqueia envio de origem cruzada. `secure: true` exige HTTPS — em dev local fica `false`. O H2 em arquivo (`jdbc:h2:file:...`) guarda a tabela de sessão em disco, pra sobreviver a restart.

Teste: loga no `mfa-demo` (admin + token), confirma que o `/admin` abre, para o app e sobe de novo. Com o mesmo navegador (mesmo cookie `JSESSIONID`), o `/admin` continua liberado — a sessão veio do banco, não da memória do processo. Com a sessão do container em memória, o restart te derrubaria.

Escolha o store pelo cenário: JDBC quando o volume de sessão é baixo e já existe banco (caso do `mfa-demo`); pra sessão em escala, há store pra Redis, mas é só trocar o starter. Com Spring Security, o `SecurityContext` é persistido na sessão, então a autenticação sobrevive a restart e load balance.

## OAuth2 / OpenID Connect (OIDC)

OAuth2 resolve a autorização: quem tem token acessa o quê. O OpenID Connect (OIDC) é o protocolo de identidade em cima do OAuth2 — o token passa a carregar também quem é o usuário. Pra autenticar contra um provedor (Google, Keycloak, Auth0), troque o decoder HMAC por um que busca as chaves do issuer. O profile `oidc` liga esse decoder e desliga o HMAC (que ganhou `@Profile("!oidc")`):

```java
package com.example.tasks.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;

@Configuration
@Profile("oidc")
public class OidcDecoderConfiguration {

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.issuer-uri}") String issuerUri) {
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }
}
```

`application-oidc.yaml`:

```yaml
spring:
  profiles:
    default: oidc

app:
  security:
    issuer-uri: http://localhost:8082/realms/tasks
```

O resource server descobre o `jwks-uri` pelo issuer, valida assinatura, emissor e expiração. O resto do filtro e das autorizações continua igual: quem muda é só de onde vêm as chaves.

Pra testar local, um Keycloak em `docker-compose.yml`:

```yaml
services:
  keycloak:
    image: quay.io/keycloak/keycloak:latest
    environment:
      KEYCLOAK_ADMIN: admin
      KEYCLOAK_ADMIN_PASSWORD: admin
    command: start-dev
    ports:
      - "8082:8080"
```

Teste:

1. `docker compose up -d` e abra `http://localhost:8082` (admin/admin).
2. Crie o realm `tasks`, um client `tasks-api` e um user com senha.
3. Peça o token no `requests.http` (o chaining salva o `access_token` em `keycloakToken`):

```http
### Token do Keycloak
POST http://localhost:8082/realms/tasks/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=password&client_id=tasks-api&client_secret=<client secret>&username=<user>&password=<senha>

> {%
    client.global.set("keycloakToken", response.body.access_token);
%}
```

4. Use o token na API (rodando com o profile `oidc`):

```http
### GET /api/tasks com token do Keycloak
GET http://localhost:8080/api/tasks
Authorization: Bearer {{keycloakToken}}
```

O login local (`/api/auth/login`) continua existindo e emite token HMAC, mas com o profile `oidc` o resource server só aceita o que o Keycloak assinou — token do login local dá 401. Pra voltar ao HMAC, rode sem o profile `oidc` (ou remova o `spring.profiles.default` do yaml).

O teste acima valida o GET autenticado. O `@PreAuthorize("hasRole('ADMIN')")` do DELETE depende do converter da claim `roles`; o Keycloak manda as roles em `realm_access`/`resource_access`, então com token dele o converter precisaria mapear essas claims — fica como exercício quando a integração exigir as roles do provedor.

## Build de cada projeto

`build.gradle.kts` do `tasks-api`, com todas as dependências da aula juntas:

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "tasks-api"

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
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
}
```

`build.gradle.kts` do `mfa-demo`:

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "mfa-demo"

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
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
    runtimeOnly("com.h2database:h2")
}
```

## Estrutura

Dois projetos. `tasks-api` na porta 8080 (API JWT, teste via `requests.http`), `mfa-demo` na porta 8081 (app de navegador, teste no browser):

```
tasks-api/src/main/java/com/example/tasks/
├── TasksApplication.java
├── auth/
│   ├── AuthController.java
│   └── TokenService.java
├── config/
│   ├── SecurityConfiguration.java
│   ├── UsersConfiguration.java
│   ├── AppUsersProperties.java
│   ├── TokenEncodingConfiguration.java
│   ├── MethodSecurityConfiguration.java
│   ├── CustomCorsConfiguration.java
│   └── OidcDecoderConfiguration.java
└── task/
    └── TaskController.java
tasks-api/src/main/resources/
├── application.yaml
├── application-oidc.yaml
├── requests.http
└── docker-compose.yml

mfa-demo/src/main/java/com/example/mfa/
├── MfaApplication.java
├── config/
│   ├── MfaConfiguration.java
│   └── ConsoleOneTimeTokenGenerationSuccessHandler.java
└── web/
    └── HomeController.java
mfa-demo/src/main/resources/
└── application.yaml
```
