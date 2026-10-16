package com.example.outbox.flight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Request body for creating a flight booking. */
public record FlightRequest(
        @NotBlank String departureAirport,
        @NotBlank String arrivalAirport,
        @NotNull Instant departureDateTime,
        @NotNull Instant arrivalDateTime) {
}
