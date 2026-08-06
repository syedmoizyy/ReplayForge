package dev.replayforge.support;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import dev.replayforge.domain.event.DomainEvent;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class EventFixture {
    private UUID eventId = UUID.randomUUID();
    private UUID aggregateId = UUID.randomUUID();
    private UUID correlationId = UUID.randomUUID();
    private String idempotencyKey = UUID.randomUUID().toString();
    private long sequence = 1;

    public static EventFixture event() { return new EventFixture(); }
    public EventFixture eventId(UUID value) { eventId = value; return this; }
    public EventFixture aggregateId(UUID value) { aggregateId = value; return this; }
    public EventFixture correlationId(UUID value) { correlationId = value; return this; }
    public EventFixture idempotencyKey(String value) { idempotencyKey = value; return this; }
    public EventFixture sequence(long value) { sequence = value; return this; }

    public DomainEvent build() {
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        return new DomainEvent(eventId, "ReservationCreated", 1, aggregateId, correlationId, null,
                idempotencyKey, sequence, time, time, JsonNodeFactory.instance.objectNode().put("currency", "USD"),
                Map.of("source", "fixture"));
    }
}
