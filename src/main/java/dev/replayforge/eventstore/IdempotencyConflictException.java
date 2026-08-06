package dev.replayforge.eventstore;
public final class IdempotencyConflictException extends EventStoreException { public IdempotencyConflictException(String message) { super(message); } }
