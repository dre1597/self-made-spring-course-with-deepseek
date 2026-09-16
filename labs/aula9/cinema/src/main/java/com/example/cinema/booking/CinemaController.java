package com.example.cinema.booking;

import java.util.List;

import com.example.cinema.screening.Screening;
import com.example.cinema.screening.ScreeningRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CinemaController {

  private final ScreeningRepository screeningRepository;
  private final BookingService bookingService;
  private final BookingRepository bookingRepository;

  public CinemaController(ScreeningRepository screeningRepository,
                          BookingService bookingService,
                          BookingRepository bookingRepository) {
    this.screeningRepository = screeningRepository;
    this.bookingService = bookingService;
    this.bookingRepository = bookingRepository;
  }

  @GetMapping("/movies/{movieId}/screenings")
  public List<Screening> screenings(@PathVariable Long movieId) {
    return screeningRepository.findByMovieId(movieId);
  }

  @PostMapping("/bookings")
  public ResponseEntity<Booking> create(@RequestBody BookingService.CreateBookingRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
  }

  @DeleteMapping("/bookings/{bookingId}")
  public ResponseEntity<Void> cancel(@PathVariable Long bookingId) {
    bookingRepository.deleteById(bookingId);
    return ResponseEntity.noContent().build();
  }
}