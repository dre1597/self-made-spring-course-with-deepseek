package com.example.cinema.screening;

import java.time.Instant;

import com.example.cinema.movie.Movie;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "screenings")
public class Screening {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  private Movie movie;

  private Instant startsAt;

  protected Screening() {
  }

  public Screening(Movie movie, Instant startsAt) {
    this.movie = movie;
    this.startsAt = startsAt;
  }

  public Long getId() {
    return id;
  }

  public Movie getMovie() {
    return movie;
  }

  public Instant getStartsAt() {
    return startsAt;
  }
}