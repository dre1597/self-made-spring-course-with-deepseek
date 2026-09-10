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