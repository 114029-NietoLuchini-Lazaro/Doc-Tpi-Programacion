CREATE TABLE processed_events (
  event_id UUID NOT NULL,
  consumer VARCHAR(100) NOT NULL,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (event_id, consumer)
);
