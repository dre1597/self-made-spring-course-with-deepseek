package com.example.books.review;

import jakarta.validation.constraints.NotBlank;

public record CreateReviewRequest(@NotBlank String comment) {
}