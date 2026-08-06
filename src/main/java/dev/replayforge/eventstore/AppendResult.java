package dev.replayforge.eventstore;

import dev.replayforge.domain.event.DomainEvent;

public record AppendResult(Status status, DomainEvent event) {
    public enum Status { APPENDED, IDEMPOTENT_REPLAY }
}
