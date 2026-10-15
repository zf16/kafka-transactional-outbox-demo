package com.example.outbox.flight.domain;

import java.time.Instant;
import java.util.UUID;

public record Flight(
        UUID id,
        String departureAirport,
        String arrivalAirport,
        Instant departureDateTime,
        Instant arrivalDateTime,
        Instant createdAt) {
}
