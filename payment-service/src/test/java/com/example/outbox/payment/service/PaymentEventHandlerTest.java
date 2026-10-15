package com.example.outbox.payment.service;

import com.example.outbox.payment.dto.FlightBookedEvent;
import com.example.outbox.payment.repository.ProcessedEventRepository;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PaymentEventHandlerTest {

    private final ProcessedEventRepository repository = mock(ProcessedEventRepository.class);
    private final PaymentEventHandler handler = new PaymentEventHandler(repository);

    @Test
    void claimsANewEvent() {
        FlightBookedEvent event = event();
        when(repository.claim(event.eventId())).thenReturn(true);

        handler.handle(event);

        verify(repository).claim(event.eventId());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void skipsAnAlreadyProcessedEvent() {
        FlightBookedEvent event = event();
        when(repository.claim(event.eventId())).thenReturn(false);

        handler.handle(event);

        verify(repository).claim(event.eventId());
        verifyNoMoreInteractions(repository);
    }

    private FlightBookedEvent event() {
        return new FlightBookedEvent(
                UUID.randomUUID(),
                "FLIGHT_BOOKED",
                Instant.parse("2026-10-14T12:00:00Z"),
                new FlightBookedEvent.FlightPayload(
                        UUID.randomUUID(),
                        "Paris",
                        "London",
                        Instant.parse("2026-11-01T08:00:00Z"),
                        Instant.parse("2026-11-01T09:15:00Z")));
    }
}
