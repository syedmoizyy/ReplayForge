package dev.replayforge.eventstore;

public sealed class EventStoreException extends RuntimeException permits DuplicateEventException, IdempotencyConflictException, SequenceConflictException {
    protected EventStoreException(String message) { super(message); }
}
