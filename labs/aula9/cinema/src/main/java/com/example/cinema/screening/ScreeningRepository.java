package com.example.cinema.screening;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScreeningRepository extends JpaRepository<Screening, Long> {

  List<Screening> findByMovieId(Long movieId);
}