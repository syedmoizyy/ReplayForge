package dev.replayforge.eventstore;

import dev.replayforge.domain.event.DomainEvent;
import java.util.List;
import java.util.UUID;

public interface EventStore {
    AppendResult append(DomainEvent event);
    List<DomainEvent> findByAggregateId(UUID aggregateId);
    List<DomainEvent> findByCorrelationId(UUID correlationId);
}
