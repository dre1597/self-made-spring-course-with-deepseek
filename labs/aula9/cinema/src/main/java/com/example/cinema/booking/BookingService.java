package com.example.cinema.booking;

import com.example.cinema.screening.Screening;
import com.example.cinema.screening.ScreeningRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

  private final BookingRepository bookingRepository;
  private final ScreeningRepository screeningRepository;
  private final ApplicationEventPublisher eventPublisher;

  public BookingService(BookingRepository bookingRepository,
                        ScreeningRepository screeningRepository,
                        ApplicationEventPublisher eventPublisher) {
    this.bookingRepository = bookingRepository;
    this.screeningRepository = screeningRepository;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public Booking create(CreateBookingRequest request) {
    Screening screening = screeningRepository.findById(request.screeningId())
        .orElseThrow(() -> new IllegalArgumentException("Sessão não encontrada"));
    Booking booking = bookingRepository.save(new Booking(screening, request.customerName()));
    eventPublisher.publishEvent(new BookingCreated(booking.getId(), screening.getId()));
    return booking;
  }

  public record CreateBookingRequest(Long screeningId, String customerName) {
  }
}