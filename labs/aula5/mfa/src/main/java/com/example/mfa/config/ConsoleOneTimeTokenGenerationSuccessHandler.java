package com.example.mfa.config;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class ConsoleOneTimeTokenGenerationSuccessHandler implements OneTimeTokenGenerationSuccessHandler {

  @Override
  public void handle(@NonNull HttpServletRequest request, HttpServletResponse response, OneTimeToken oneTimeToken)
      throws IOException {
    IO.println("[OTT] token de " + oneTimeToken.getUsername() + ": " + oneTimeToken.getTokenValue());
    response.sendRedirect("/login/ott?token=" + oneTimeToken.getTokenValue());
  }
}