package com.example.outbox.payment.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.sql.Timestamp;
import java.util.UUID;

@Repository
public class ProcessedEventRepository {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventRepository.class);

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
        log.debug("claim called: eventId={}", eventId);
        int inserted = jdbcClient.sql("""
                        INSERT INTO processed_events (event_id, processed_at)
                        VALUES (:eventId, :processedAt)
                        ON CONFLICT (event_id) DO NOTHING
                        """)
                .param("eventId", eventId)
                .param("processedAt", Timestamp.from(clock.instant()))
                .update();
        boolean claimed = inserted == 1;
        log.debug("claim completed: eventId={} claimed={}", eventId, claimed);
        return claimed;
    }
}
