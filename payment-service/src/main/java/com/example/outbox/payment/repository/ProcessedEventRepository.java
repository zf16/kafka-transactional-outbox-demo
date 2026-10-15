package com.example.outbox.payment.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.sql.Timestamp;
import java.util.UUID;

@Repository
public class ProcessedEventRepository {

    private final JdbcClient jdbcClient;
    private final Clock clock;

    @Autowired
    public ProcessedEventRepository(JdbcClient jdbcClient) {
        this(jdbcClient, Clock.systemUTC());
    }

    ProcessedEventRepository(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    public boolean claim(UUID eventId) {
        int inserted = jdbcClient.sql("""
                        INSERT INTO processed_events (event_id, processed_at)
                        VALUES (:eventId, :processedAt)
                        ON CONFLICT (event_id) DO NOTHING
                        """)
                .param("eventId", eventId)
                .param("processedAt", Timestamp.from(clock.instant()))
                .update();
        return inserted == 1;
    }
}
