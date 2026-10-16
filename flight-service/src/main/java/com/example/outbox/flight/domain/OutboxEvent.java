package com.example.outbox.flight.domain;

import java.time.Instant;
import java.util.UUID;

/** Read model for inspecting a retained transactional outbox event. */
public record OutboxEvent(
        long id,
        UUID eventId,
        UUID flightId,
        String eventType,
        String payload,
        Instant createdAt,
        Instant processedAt) {
}
