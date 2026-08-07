package dev.replayforge.replay;

import dev.replayforge.domain.event.DomainEvent;
import java.util.UUID;

public record ReplayedEvent(long order, UUID sourceEventId, DomainEvent event) {}
