package com.example.outbox.flight.dto;

import com.example.outbox.flight.domain.OutboxEvent;

import java.time.Instant;
import java.util.UUID;

/** Demo-only representation of a retained outbox event and its processing state. */
public record OutboxEventResponse(
        long id,
        UUID eventId,
        UUID flightId,
        String eventType,
        String payload,
        Instant createdAt,
        Instant processedAt,
        String status) {

    public static OutboxEventResponse from(OutboxEvent event) {
        return new OutboxEventResponse(
                event.id(),
                event.eventId(),
                event.flightId(),
                event.eventType(),
                event.payload(),
                event.createdAt(),
                event.processedAt(),
                event.processedAt() == null ? "PENDING" : "PROCESSED");
    }
}
