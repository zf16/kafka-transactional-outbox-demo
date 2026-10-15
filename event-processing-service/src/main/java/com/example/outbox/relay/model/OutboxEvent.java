package com.example.outbox.relay.model;

import java.util.UUID;

public record OutboxEvent(long id, UUID eventId, UUID flightId, String payload) {
}
