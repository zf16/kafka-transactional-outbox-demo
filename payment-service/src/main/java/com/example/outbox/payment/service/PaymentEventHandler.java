package com.example.outbox.payment.service;

import com.example.outbox.payment.dto.FlightBookedEvent;
import com.example.outbox.payment.repository.ProcessedEventRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentEventHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventHandler.class);

    private final ProcessedEventRepository processedEventRepository;

    public PaymentEventHandler(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional
    public void handle(FlightBookedEvent event) {
        if (!"FLIGHT_BOOKED".equals(event.eventType())) {
            throw new IllegalArgumentException("Unsupported event type: " + event.eventType());
        }
        if (event.eventId() == null || event.flight() == null || event.flight().id() == null) {
            throw new IllegalArgumentException("Event ID and flight are required");
        }

        if (!processedEventRepository.claim(event.eventId())) {
            log.info("Skipping duplicate eventId={} flightId={}", event.eventId(), event.flight().id());
            return;
        }

        log.info("Processing payment for flight {}: {} -> {} (eventId={})",
                event.flight().id(),
                event.flight().departureAirport(),
                event.flight().arrivalAirport(),
                event.eventId());
    }
}
