package dev.replayforge.eventstore;

import dev.replayforge.domain.event.DomainEvent;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

public interface EventStore {
    AppendResult append(DomainEvent event);
    List<DomainEvent> findByAggregateId(UUID aggregateId);
    List<DomainEvent> findByCorrelationId(UUID correlationId);
    List<TraceSummary> findTraces(int limit);

    record TraceSummary(UUID correlationId, UUID aggregateId, long eventCount, Instant firstRecordedAt,
            Instant lastRecordedAt, String lastEventType) {}
}
