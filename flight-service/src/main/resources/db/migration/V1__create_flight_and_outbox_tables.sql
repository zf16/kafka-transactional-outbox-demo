CREATE TABLE flights (
    id UUID PRIMARY KEY,
    departure_airport VARCHAR(100) NOT NULL,
    arrival_airport VARCHAR(100) NOT NULL,
    departure_date_time TIMESTAMPTZ NOT NULL,
    arrival_date_time TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT flight_airports_different CHECK (LOWER(departure_airport) <> LOWER(arrival_airport)),
    CONSTRAINT flight_times_ordered CHECK (arrival_date_time > departure_date_time)
);

CREATE TABLE flight_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    flight_id UUID NOT NULL REFERENCES flights(id),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX flight_outbox_created_order_idx ON flight_outbox (id);
