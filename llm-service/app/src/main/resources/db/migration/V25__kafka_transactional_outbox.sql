CREATE TABLE outbox_events (
  id UUID PRIMARY KEY,
  topic VARCHAR(120) NOT NULL,
  event_key VARCHAR(120) NOT NULL,
  payload JSONB NOT NULL,
  traceparent VARCHAR(128),
  request_id VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  published_at TIMESTAMPTZ
);
CREATE INDEX outbox_events_unpublished_idx ON outbox_events (created_at) WHERE published_at IS NULL;
