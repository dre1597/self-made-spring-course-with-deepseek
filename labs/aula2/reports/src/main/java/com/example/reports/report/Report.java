package com.example.reports.report;

import java.time.Instant;

public record Report(String title, String content, Instant startedAt) {
}