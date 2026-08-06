package dev.replayforge.domain.event;

public final class EventSerializationException extends RuntimeException {
    public EventSerializationException(String message) { super(message); }
    public EventSerializationException(String message, Throwable cause) { super(message, cause); }
}
