# Aula 05 — Segurança

Objetivo: proteger a API com Spring Security 7, autenticação JWT stateless e MFA nativo.

## Dependências

```kotlin
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
}
```

O `spring-boot-starter-security` liga a proteção em toda a aplicação assim que entra no classpath. O `oauth2-resource-server` traz o suporte a JWT (validar token).

## CSRF e o que mudou no Security 7

CSRF vem ligado por default, e o Security 7 mudou o DSL:

- `and()` sumiu; cada seção usa lambda.
- `authorizeRequests` virou `authorizeHttpRequests`.
- A configuração é modular, via `SecurityFilterChain`.

CSRF protege formulários de navegador contra envio forjado. Numa API stateless com JWT, o token via no header `Authorization`, não em cookie que o navegador envia sozinho. Então CSRF não protege nada aqui e se desliga.

## Autenticação JWT stateless

Configuração principal:

```java
package com.example.books.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/books/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.jwt-secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
```

Ponto a ponto:

- `csrf(csrf -> csrf.disable())`: desliga CSRF, inútil em API stateless.
- `authorizeHttpRequests`: `/api/books/**` aberto, o resto exige autenticação.
- `oauth2ResourceServer(...)`: a API passa a aceitar `Authorization: Bearer <token>`.
- `sessionManagement(...STATELESS)`: nada de sessão; cada requisição se autentica pelo token.
- `jwtDecoder`: valida assinatura HMAC. O segredo vem de `app.security.jwt-secret`, que vem do ambiente:

```yaml
app:
  security:
    jwt-secret: ${JWT_SECRET}
```

O segredo nunca fica no código. `JWT_SECRET` é variável de ambiente.

O token em si é emitido por um servidor de autorização (outro serviço, com `NimbusJwtEncoder`). O resource server aqui só valida. Separar emissão de validação é o desenho padrão: o auth server assina, os demais serviços validam.

## Authorization Server (emissão)

Quem emite o token é um serviço de autorização, com a mesma chave do resource server. O `NimbusJwtEncoder` assina o JWT:

```java
package com.example.auth.config;

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
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }
}
```

O serviço monta as claims e devolve o token:

```java
package com.example.auth.token;

import java.time.Instant;
import java.util.List;

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
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("books-auth")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .subject(username)
                .claim("roles", roles)
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
```

O resource server valida com o `NimbusJwtDecoder` da seção anterior, usando a mesma chave. O `issuer` precisa bater se o decoder valida emissor. Esse desenho serve pra um token simples emitido por um serviço próprio.

Pra OAuth2/OIDC completo (clientes, refresh token, consentimento, logout), use o Spring Authorization Server, `spring-boot-starter-oauth2-authorization-server`. Ele traz a infra inteira; o encoder manual cobre o caso onde você só precisa assinar um token.

## Password encoding

Senha nunca vai pro banco em texto. `PasswordEncoder` faz o hash:

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

```java
PasswordEncoder encoder = new BCryptPasswordEncoder();
String hash = encoder.encode("senha");
boolean confere = encoder.matches("senha", hash);
```

BCrypt é o padrão. Argon2 e scrypt são alternativas mais recentes. O `DelegatingPasswordEncoder` guarda o algoritmo no hash (`{bcrypt}...`) e permite migrar de algoritmo sem re-hash de tudo de uma vez.

## Autorização por método

Além de proteger por URL, dá pra proteger por método com `@PreAuthorize`.

```java
package com.example.books.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity
public class MethodSecurityConfiguration {
}
```

```java
@PreAuthorize("hasRole('ADMIN')")
public void delete(Long id) {
    repository.deleteById(id);
}
```

A regra vale em qualquer chamada ao método, venha de controller, de serviço ou de evento. Expressions comuns: `hasRole('ADMIN')`, `hasAuthority('SCOPE_books.write')`, `isAuthenticated()`.

## CORS

CORS decide se um navegador numa origem pode chamar a API noutra origem. Com frontend separado, o navegador bloqueia a resposta sem o header de permissão. Configure as origens no filtro:

```java
package com.example.books.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfiguration {

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://app.exemplo.com"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

O bean entra no filtro com `http.cors(Customizer.withDefaults())`. Aponte a origem exata, nunca `*` com credencial junto. CORS protege o navegador, não a API: quem autoriza o acesso é o token, não o header.

## MFA nativo

Cada fator de autenticação vira uma `FactorGrantedAuthority`. Autenticou com senha, ganha `PASSWORD_AUTHORITY`; completou one-time token, ganha `OTT_AUTHORITY`. A autorização então exige quantos fatores quiser.

O `@EnableMultiFactorAuthentication` aplica a exigência a todas as regras:

```java
package com.example.books.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authorization.EnableMultiFactorAuthentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.FactorGrantedAuthority;
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
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(Customizer.withDefaults())
                .oneTimeTokenLogin(Customizer.withDefaults());
        return http.build();
    }
}
```

Todo endpoint exige senha e one-time token. Quando falta um fator, o Spring redireciona pro login do fator que falta, sem lógica customizada.

Pra exigir MFA só em parte do app, use `AuthorizationManagerFactories` no lugar da anotação:

```java
AuthorizationManagerFactory<Object> mfa = AuthorizationManagerFactories.multiFactor()
        .requireFactors(
                FactorGrantedAuthority.PASSWORD_AUTHORITY,
                FactorGrantedAuthority.OTT_AUTHORITY)
        .build();

http.authorizeHttpRequests(authorize -> authorize
        .requestMatchers("/admin/**").access(mfa.hasRole("ADMIN"))
        .anyRequest().authenticated());
```

MFA por redirecionamento de login é pra app com navegador (form login + OTT), não pra API stateless. Numa API, o segundo fator chega como outro token ou header, e a regra de fatores entra nas authorities do JWT.

## Spring Session

JWT stateless resolve a API. App de navegador (portal, backoffice) usa sessão, e aí vem o problema: o container guarda a sessão na memória do nó, então duas instâncias atrás de um load balancer derrubam o usuário a cada requisição que cai no nó errado. Spring Session move a sessão pra um store compartilhado.

```kotlin
implementation("org.springframework.boot:spring-boot-starter-session-jdbc")
// ou, pra Redis:
implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
```

O código continua usando `HttpSession`; o Boot troca a implementação por trás. Com JDBC a sessão vira linha na tabela, com Redis vira chave com TTL. O `springSessionRepositoryFilter` intercepta a requisição e substitui a sessão do container.

O cookie que carrega o id da sessão precisa de flags:

```yaml
server:
  servlet:
    session:
      timeout: 30m
      cookie:
        http-only: true
        secure: true
        same-site: lax
```

`http-only` impede leitura por JavaScript, `secure` manda o cookie só por HTTPS, `same-site` bloqueia envio de origem cruzada. `secure: true` exige HTTPS; desligue só em dev local.

Escolha o store pelo cenário: JDBC quando o volume de sessão é baixo e já existe banco; Redis quando escala e a sessão é efêmera. Com Spring Security, o `SecurityContext` é persistido na sessão, então a autenticação sobrevive a restart e load balance.

## OAuth2 / OIDC

Pra autenticar contra um provedor (Google, Keycloak, Auth0), troque o decoder HMAC por um que busca as chaves do issuer:

```java
@Bean
JwtDecoder jwtDecoder() {
    return JwtDecoders.fromIssuerLocation("https://id.exemplo.com/realms/meu-realm");
}
```

O resource server descobre o `jwks-uri` pelo issuer, valida assinatura, emissor e expiração. O resto do filtro e das autorizações continua igual: quem muda é só de onde vêm as chaves.

## Estrutura

```
src/main/java/com/example/books/
├── BooksApplication.java
├── book/
│   ├── Book.java
│   ├── BookRepository.java
│   ├── BookService.java
│   └── BookController.java
└── config/
    ├── SecurityConfiguration.java
    ├── MethodSecurityConfiguration.java
    └── CorsConfiguration.java
src/main/java/com/example/auth/
├── AuthApplication.java
├── token/
│   └── TokenService.java
└── config/
    └── TokenEncodingConfiguration.java
src/main/resources/
└── application.yaml
```
