package dev.replayforge.invariants;

import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.replay.ReplayState;
import java.util.List;

public record InvariantContext(DomainEvent event, ReplayState before, ReplayState after,
        List<DomainEvent> eventsSeen, long eventPosition, boolean replayComplete) {
    public InvariantContext { eventsSeen = List.copyOf(eventsSeen); }
}
