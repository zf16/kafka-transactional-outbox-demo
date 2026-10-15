package com.example.outbox.payment.dto;

import java.time.Instant;
import java.util.UUID;

public record FlightBookedEvent(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        FlightPayload flight) {

    public record FlightPayload(
            UUID id,
            String departureAirport,
            String arrivalAirport,
            Instant departureDateTime,
            Instant arrivalDateTime) {
    }
}
