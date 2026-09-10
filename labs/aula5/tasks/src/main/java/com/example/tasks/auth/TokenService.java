package com.example.tasks.auth;

import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
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

    var header = JwsHeader.with(MacAlgorithm.HS256).build();

    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

  }
}