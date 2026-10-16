ALTER TABLE flight_outbox
    ADD COLUMN processed_at TIMESTAMPTZ;

CREATE INDEX flight_outbox_pending_order_idx
    ON flight_outbox (id)
    WHERE processed_at IS NULL;
