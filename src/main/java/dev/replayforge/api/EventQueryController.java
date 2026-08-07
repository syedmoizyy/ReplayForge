package dev.replayforge.api;

import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.eventstore.EventStore;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1")
@Validated
public final class EventQueryController {
    private final EventStore eventStore;
    public EventQueryController(EventStore eventStore) { this.eventStore = eventStore; }

    @GetMapping("/traces")
    public List<EventStore.TraceSummary> traces(@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return eventStore.findTraces(limit);
    }

    @GetMapping("/traces/{correlationId}")
    public List<DomainEvent> trace(@PathVariable UUID correlationId) { return eventStore.findByCorrelationId(correlationId); }

    @GetMapping("/aggregates/{aggregateId}/events")
    public List<DomainEvent> aggregate(@PathVariable UUID aggregateId) { return eventStore.findByAggregateId(aggregateId); }
}
