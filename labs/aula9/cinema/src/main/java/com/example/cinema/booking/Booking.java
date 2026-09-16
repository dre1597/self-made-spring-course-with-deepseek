package com.example.cinema.booking;

import com.example.cinema.screening.Screening;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "bookings")
public class Booking {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  private Screening screening;

  private String customerName;

  protected Booking() {
  }

  public Booking(Screening screening, String customerName) {
    this.screening = screening;
    this.customerName = customerName;
  }

  public Long getId() {
    return id;
  }

  public Screening getScreening() {
    return screening;
  }

  public String getCustomerName() {
    return customerName;
  }
}