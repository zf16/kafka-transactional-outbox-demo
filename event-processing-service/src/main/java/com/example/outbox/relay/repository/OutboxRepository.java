package com.example.outbox.relay.repository;

import com.example.outbox.relay.model.OutboxEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxRepository {

    private static final Logger log = LoggerFactory.getLogger(OutboxRepository.class);

    private final JdbcClient jdbcClient;

    public OutboxRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<OutboxEvent> lockNextBatch(int batchSize) {
        log.debug("lockNextBatch called: batchSize={}", batchSize);
        List<OutboxEvent> events = jdbcClient.sql("""
                        SELECT id, event_id, flight_id, payload::text AS payload
                        FROM flight_outbox
                        WHERE processed_at IS NULL
                        ORDER BY id
                        LIMIT :batchSize
                        FOR UPDATE SKIP LOCKED
                        """)
                .param("batchSize", batchSize)
                .query((rs, rowNum) -> new OutboxEvent(
                        rs.getLong("id"),
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("flight_id", UUID.class),
                        rs.getString("payload")))
                .list();
        log.debug("lockNextBatch completed: count={}", events.size());
        return events;
    }

    public void markProcessed(long id, Instant processedAt) {
        log.debug("markProcessed called: outboxId={}", id);
        int updated = jdbcClient.sql("""
                        UPDATE flight_outbox
                        SET processed_at = :processedAt
                        WHERE id = :id AND processed_at IS NULL
                        """)
                .param("id", id)
                .param("processedAt", Timestamp.from(processedAt))
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Expected to mark one outbox row processed, updated " + updated);
        }
        log.debug("markProcessed completed: outboxId={}", id);
    }
}
