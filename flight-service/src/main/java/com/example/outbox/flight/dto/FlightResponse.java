package com.example.outbox.flight.dto;

import com.example.outbox.flight.domain.Flight;

import java.time.Instant;
import java.util.UUID;

/** Stable version 1 representation of a booked flight. */
public record FlightResponse(
        UUID id,
        String departureAirport,
        String arrivalAirport,
        Instant departureDateTime,
        Instant arrivalDateTime,
        Instant createdAt) {

    public static FlightResponse from(Flight flight) {
        return new FlightResponse(
                flight.id(),
                flight.departureAirport(),
                flight.arrivalAirport(),
                flight.departureDateTime(),
                flight.arrivalDateTime(),
                flight.createdAt());
    }
}
