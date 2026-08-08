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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Validated
@Tag(name = "Event traces", description = "Captured workflow timelines and aggregate event streams")
public final class EventQueryController {
    private final EventStore eventStore;
    public EventQueryController(EventStore eventStore) { this.eventStore = eventStore; }

    @GetMapping("/traces")
    @Operation(summary = "List recent workflow traces", description = "Returns information-dense summaries ordered by recent capture time.")
    public List<EventStore.TraceSummary> traces(@Parameter(example = "50") @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        return eventStore.findTraces(limit);
    }

    @GetMapping("/traces/{correlationId}")
    @Operation(summary = "Read a complete event timeline by correlation ID")
    public List<DomainEvent> trace(@PathVariable UUID correlationId) { return eventStore.findByCorrelationId(correlationId); }

    @GetMapping("/aggregates/{aggregateId}/events")
    @Operation(summary = "Read an aggregate event stream")
    public List<DomainEvent> aggregate(@PathVariable UUID aggregateId) { return eventStore.findByAggregateId(aggregateId); }
}
