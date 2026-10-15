package com.example.outbox.relay.repository;

import com.example.outbox.relay.model.OutboxEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class OutboxRepository {

    private final JdbcClient jdbcClient;

    public OutboxRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<OutboxEvent> lockNextBatch(int batchSize) {
        return jdbcClient.sql("""
                        SELECT id, event_id, flight_id, payload::text AS payload
                        FROM flight_outbox
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
    }

    public void delete(long id) {
        jdbcClient.sql("DELETE FROM flight_outbox WHERE id = :id")
                .param("id", id)
                .update();
    }
}
