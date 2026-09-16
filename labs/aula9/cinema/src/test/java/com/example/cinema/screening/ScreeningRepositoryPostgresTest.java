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
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

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