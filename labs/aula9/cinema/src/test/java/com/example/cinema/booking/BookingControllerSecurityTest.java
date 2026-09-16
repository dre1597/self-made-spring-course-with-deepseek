package com.example.cinema.booking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import com.example.cinema.config.SecurityConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(CinemaController.class)
@Import(SecurityConfiguration.class)
class BookingControllerSecurityTest {

  @Autowired
  MockMvcTester mvc;

  @MockitoBean
  BookingService bookingService;

  @MockitoBean
  BookingRepository bookingRepository;

  @MockitoBean
  com.example.cinema.screening.ScreeningRepository screeningRepository;

  @Test
  @WithMockUser(roles = "STAFF")
  void staffCanCancelBooking() {
    assertThat(mvc.delete().uri("/api/bookings/1")).hasStatus(204);
  }

  @Test
  void anonymousUserCannotCancelBooking() {
    assertThat(mvc.delete().uri("/api/bookings/1")).hasStatus(401);
  }

  @Test
  @WithMockUser(roles = "CUSTOMER")
  void customerCannotCancelBooking() {
    assertThat(mvc.delete().uri("/api/bookings/1")).hasStatus(403);
  }
}