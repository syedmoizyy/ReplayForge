package dev.replayforge.domain.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record DomainEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        UUID aggregateId,
        UUID correlationId,
        UUID causationId,
        String idempotencyKey,
        long sequenceNumber,
        Instant occurredAt,
        Instant recordedAt,
        JsonNode payload,
        Map<String, String> metadata) {

    public DomainEvent {
        Objects.requireNonNull(eventId, "eventId is required");
        requireText(eventType, "eventType");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion must be positive");
        Objects.requireNonNull(aggregateId, "aggregateId is required");
        Objects.requireNonNull(correlationId, "correlationId is required");
        requireText(idempotencyKey, "idempotencyKey");
        if (sequenceNumber < 1) throw new IllegalArgumentException("sequenceNumber must be positive");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(recordedAt, "recordedAt is required");
        Objects.requireNonNull(payload, "payload is required");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
