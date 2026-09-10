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