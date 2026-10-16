package com.example.outbox.flight.repository;

import com.example.outbox.flight.domain.Flight;
import com.example.outbox.flight.domain.OutboxEvent;
import com.example.outbox.flight.dto.FlightBookedEvent;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FlightRepository {

    private static final Logger log = LoggerFactory.getLogger(FlightRepository.class);

    private final JdbcClient jdbcClient;

    public FlightRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insertFlight(Flight flight) {
        log.debug("insertFlight called: flightId={}", flight.id());
        jdbcClient.sql("""
                        INSERT INTO flights
                            (id, departure_airport, arrival_airport, departure_date_time, arrival_date_time, created_at)
                        VALUES (:id, :departureAirport, :arrivalAirport, :departureDateTime, :arrivalDateTime, :createdAt)
                        """)
                .param("id", flight.id())
                .param("departureAirport", flight.departureAirport())
                .param("arrivalAirport", flight.arrivalAirport())
                .param("departureDateTime", java.sql.Timestamp.from(flight.departureDateTime()))
                .param("arrivalDateTime", java.sql.Timestamp.from(flight.arrivalDateTime()))
                .param("createdAt", java.sql.Timestamp.from(flight.createdAt()))
                .update();
        log.debug("insertFlight completed: flightId={}", flight.id());
    }

    public void insertOutboxEvent(FlightBookedEvent event, String payload) {
        log.debug("insertOutboxEvent called: eventId={} flightId={}", event.eventId(), event.flight().id());
        jdbcClient.sql("""
                        INSERT INTO flight_outbox (event_id, flight_id, event_type, payload, created_at)
                        VALUES (:eventId, :flightId, :eventType, CAST(:payload AS jsonb), :createdAt)
                        """)
                .param("eventId", event.eventId())
                .param("flightId", event.flight().id())
                .param("eventType", event.eventType())
                .param("payload", payload)
                .param("createdAt", java.sql.Timestamp.from(event.occurredAt()))
                .update();
        log.debug("insertOutboxEvent completed: eventId={}", event.eventId());
    }

    public Optional<Flight> findById(UUID id) {
        log.debug("findById called: flightId={}", id);
        Optional<Flight> flight = jdbcClient.sql("""
                        SELECT id, departure_airport, arrival_airport,
                               departure_date_time, arrival_date_time, created_at
                        FROM flights
                        WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new Flight(
                        rs.getObject("id", UUID.class),
                        rs.getString("departure_airport"),
                        rs.getString("arrival_airport"),
                        rs.getTimestamp("departure_date_time").toInstant(),
                        rs.getTimestamp("arrival_date_time").toInstant(),
                        rs.getTimestamp("created_at").toInstant()))
                .optional();
        log.debug("findById completed: flightId={} found={}", id, flight.isPresent());
        return flight;
    }

    public List<OutboxEvent> findAllOutboxEvents() {
        log.debug("findAllOutboxEvents called");
        List<OutboxEvent> events = jdbcClient.sql("""
                        SELECT id, event_id, flight_id, event_type, payload::text AS payload,
                               created_at, processed_at
                        FROM flight_outbox
                        ORDER BY id
                        """)
                .query((rs, rowNum) -> new OutboxEvent(
                        rs.getLong("id"),
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("flight_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("processed_at") == null
                                ? null
                                : rs.getTimestamp("processed_at").toInstant()))
                .list();
        log.debug("findAllOutboxEvents completed: count={}", events.size());
        return events;
    }
}
