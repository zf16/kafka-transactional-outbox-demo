package com.example.outbox.flight.repository;

import com.example.outbox.flight.domain.Flight;
import com.example.outbox.flight.dto.FlightBookedEvent;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class FlightRepository {

    private final JdbcClient jdbcClient;

    public FlightRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insertFlight(Flight flight) {
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
    }

    public void insertOutboxEvent(FlightBookedEvent event, String payload) {
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
    }

    public Optional<Flight> findById(UUID id) {
        return jdbcClient.sql("""
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
    }

    public List<OutboxView> findPendingEvents() {
        return jdbcClient.sql("""
                        SELECT id, event_id, flight_id, event_type, payload::text AS payload, created_at
                        FROM flight_outbox
                        ORDER BY id
                        """)
                .query((rs, rowNum) -> new OutboxView(
                        rs.getLong("id"),
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("flight_id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    public record OutboxView(
            long id,
            UUID eventId,
            UUID flightId,
            String eventType,
            String payload,
            Instant createdAt) {
    }
}
