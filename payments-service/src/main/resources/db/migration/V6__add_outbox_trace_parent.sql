-- W3C traceparent of the request that recorded the event, so the trace continues through Kafka
-- even though the relay sends the event later, on another thread.
ALTER TABLE outbox_events ADD COLUMN trace_parent varchar(64);
