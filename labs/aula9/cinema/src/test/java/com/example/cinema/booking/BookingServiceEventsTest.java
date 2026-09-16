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