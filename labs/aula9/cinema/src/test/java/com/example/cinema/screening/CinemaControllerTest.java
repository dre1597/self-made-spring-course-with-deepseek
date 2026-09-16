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