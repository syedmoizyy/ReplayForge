package dev.replayforge.sampleworkload;

import dev.replayforge.domain.event.DomainEvent;

public interface WorkflowOutbox {
    void enqueue(DomainEvent event);
}
