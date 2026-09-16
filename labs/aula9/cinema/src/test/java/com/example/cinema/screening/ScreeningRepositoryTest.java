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