package com.example.cinema.booking;

import java.time.Instant;

import com.example.cinema.movie.Movie;
import com.example.cinema.movie.MovieRepository;
import com.example.cinema.screening.Screening;
import com.example.cinema.screening.ScreeningRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookingServiceTest {

  @Autowired
  BookingService bookingService;

  @Autowired
  MovieRepository movieRepository;

  @Autowired
  ScreeningRepository screeningRepository;

  @Test
  void createsBookingForExistingScreening() {
    Movie movie = movieRepository.save(new Movie("Duna: Parte Dois"));
    Screening screening = screeningRepository.save(
        new Screening(movie, Instant.parse("2026-10-10T20:00:00Z")));

    Booking booking = bookingService.create(
        new BookingService.CreateBookingRequest(screening.getId(), "Ana"));

    assertThat(booking.getId()).isNotNull();
    assertThat(booking.getCustomerName()).isEqualTo("Ana");
  }
}