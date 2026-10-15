package com.example.outbox.relay.service;

import java.util.UUID;

final class OutboxPublicationException extends RuntimeException {

    OutboxPublicationException(UUID eventId, Throwable cause) {
        super("Could not publish outbox event " + eventId, cause);
    }
}
