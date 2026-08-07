package dev.replayforge.faults;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.faults.FaultSchedule.ExecutionDirective;
import dev.replayforge.faults.FaultSchedule.FaultDecision;
import dev.replayforge.faults.FaultSchedule.ScheduledEvent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Compiles faults in declaration order into an auditable, deterministic schedule. */
public final class FaultScheduleCompiler {
    public FaultSchedule compile(FaultScenario scenario, List<DomainEvent> source) {
        if (source.size() > scenario.limits().maxEvents())
            throw new FaultValidationException("source has " + source.size() + " events; maxEvents is " + scenario.limits().maxEvents());
        List<ScheduledEvent> schedule = new ArrayList<>();
        source.forEach(event -> schedule.add(new ScheduledEvent(event, 1, 0, "source:" + event.eventId())));
        List<FaultDecision> decisions = new ArrayList<>();
        List<ExecutionDirective> directives = new ArrayList<>();

        for (FaultSpec fault : scenario.faults()) {
            validateLimits(fault, scenario.limits());
            List<ScheduledEvent> candidates = List.copyOf(schedule);
            boolean applied = false;
            for (ScheduledEvent item : candidates) {
                FaultSelector.Match match = fault.selector().match(item.event(), item.attempt(), scenario.seed(), fault.id());
                if (!match.matched()) {
                    decisions.add(decision(fault, item, FaultDecision.Outcome.SKIPPED, match.rationale()));
                    continue;
                }
                applied = true;
                apply(fault, item, schedule, directives);
                decisions.add(decision(fault, item, FaultDecision.Outcome.APPLIED, match.rationale() + "; " + effectRationale(fault)));
                ensureBounded(schedule, scenario.limits());
            }
            if (fault.type() == FaultType.REORDER && applied) reorder(fault, schedule, scenario.seed());
        }
        schedule.sort(Comparator.comparingLong(ScheduledEvent::logicalDelayMillis));
        return new FaultSchedule(schedule, decisions, directives);
    }

    private void apply(FaultSpec fault, ScheduledEvent item, List<ScheduledEvent> schedule,
            List<ExecutionDirective> directives) {
        switch (fault.type()) {
            case DUPLICATE -> {
                int count = integer(fault, "count");
                int index = schedule.indexOf(item) + 1;
                for (int copy = 1; copy <= count; copy++) schedule.add(index++, new ScheduledEvent(
                        copyEvent(item.event(), fault.id(), copy), item.attempt(), item.logicalDelayMillis(), "fault:" + fault.id()));
            }
            case DROP -> schedule.remove(item);
            case DELAY -> replace(schedule, item, new ScheduledEvent(item.event(), item.attempt(),
                    item.logicalDelayMillis() + longValue(fault, "durationMillis"), "fault:" + fault.id()));
            case RETRY_STORM -> {
                int retries = integer(fault, "retries");
                int index = schedule.indexOf(item) + 1;
                for (int attempt = 2; attempt <= retries + 1; attempt++) schedule.add(index++,
                        new ScheduledEvent(item.event(), attempt, item.logicalDelayMillis(), "fault:" + fault.id()));
            }
            case MALFORMED_PAYLOAD -> replace(schedule, item, new ScheduledEvent(malformed(item.event(), fault),
                    item.attempt(), item.logicalDelayMillis(), "fault:" + fault.id()));
            case WORKER_CRASH -> directives.add(new ExecutionDirective(fault.id(), fault.type(), item.event().eventId(),
                    boundary(fault), effectRationale(fault)));
            case DEPENDENCY_TIMEOUT -> directives.add(new ExecutionDirective(fault.id(), fault.type(), item.event().eventId(),
                    ExecutionDirective.Boundary.DEPENDENCY_CALL, effectRationale(fault)));
            case REORDER -> { /* applied once after all selector evaluations */ }
        }
    }

    private void reorder(FaultSpec fault, List<ScheduledEvent> schedule, long seed) {
        String position = text(fault, "position");
        List<ScheduledEvent> matched = schedule.stream().filter(item ->
                fault.selector().match(item.event(), item.attempt(), seed, fault.id()).matched()).toList();
        schedule.removeAll(matched);
        if ("FIRST".equals(position)) schedule.addAll(0, matched);
        else if ("LAST".equals(position)) schedule.addAll(matched);
        else throw new FaultValidationException("REORDER position must be FIRST or LAST");
    }

    private DomainEvent malformed(DomainEvent event, FaultSpec fault) {
        int schemaVersion = fault.parameters().containsKey("schemaVersion") ? integer(fault, "schemaVersion") : event.schemaVersion();
        JsonNode payload = event.payload().deepCopy();
        JsonNode patch = fault.parameters().get("payloadPatch");
        if (patch != null) {
            if (!(payload instanceof ObjectNode target) || !patch.isObject())
                throw new FaultValidationException("payloadPatch requires object payload and patch");
            patch.fields().forEachRemaining(entry -> target.set(entry.getKey(), entry.getValue()));
        }
        return new DomainEvent(event.eventId(), event.eventType(), schemaVersion, event.aggregateId(), event.correlationId(),
                event.causationId(), event.idempotencyKey(), event.sequenceNumber(), event.occurredAt(), event.recordedAt(),
                payload, event.metadata());
    }

    private void validateLimits(FaultSpec fault, FaultLimits limits) {
        if (fault.type() == FaultType.DUPLICATE && integer(fault, "count") > limits.maxDuplicates())
            throw new FaultValidationException("fault " + fault.id() + " exceeds maxDuplicates " + limits.maxDuplicates());
        if (fault.type() == FaultType.DELAY && longValue(fault, "durationMillis") > limits.maxDelayMillis())
            throw new FaultValidationException("fault " + fault.id() + " exceeds maxDelayMillis " + limits.maxDelayMillis());
        if (fault.type() == FaultType.RETRY_STORM && integer(fault, "retries") > limits.maxRetries())
            throw new FaultValidationException("fault " + fault.id() + " exceeds maxRetries " + limits.maxRetries());
    }

    private void ensureBounded(List<ScheduledEvent> schedule, FaultLimits limits) {
        if (schedule.size() > limits.maxEvents())
            throw new FaultValidationException("compiled schedule exceeds maxEvents " + limits.maxEvents());
        if (schedule.stream().anyMatch(item -> item.logicalDelayMillis() > limits.maxDelayMillis()))
            throw new FaultValidationException("cumulative logical delay exceeds maxDelayMillis " + limits.maxDelayMillis());
    }
    private FaultDecision decision(FaultSpec fault, ScheduledEvent item, FaultDecision.Outcome outcome, String rationale) {
        return new FaultDecision(fault.id(), fault.type(), item.event().eventId(), outcome, rationale);
    }
    private void replace(List<ScheduledEvent> schedule, ScheduledEvent oldItem, ScheduledEvent replacement) {
        schedule.set(schedule.indexOf(oldItem), replacement);
    }
    private UUID copyId(UUID original, String faultId, int copy) {
        return UUID.nameUUIDFromBytes((original + ":" + faultId + ":" + copy).getBytes(StandardCharsets.UTF_8));
    }
    private DomainEvent copyEvent(DomainEvent event, String faultId, int copy) {
        return new DomainEvent(copyId(event.eventId(), faultId, copy), event.eventType(), event.schemaVersion(), event.aggregateId(),
                event.correlationId(), event.eventId(), event.idempotencyKey() + ":duplicate:" + copy, event.sequenceNumber(),
                event.occurredAt(), event.recordedAt(), event.payload(), event.metadata());
    }
    private int integer(FaultSpec fault, String key) {
        JsonNode value = required(fault, key);
        if (!value.canConvertToInt() || value.intValue() < 0) throw new FaultValidationException(key + " must be a non-negative integer");
        return value.intValue();
    }
    private long longValue(FaultSpec fault, String key) {
        JsonNode value = required(fault, key);
        if (!value.canConvertToLong() || value.longValue() < 0) throw new FaultValidationException(key + " must be a non-negative integer");
        return value.longValue();
    }
    private String text(FaultSpec fault, String key) {
        JsonNode value = required(fault, key);
        if (!value.isTextual()) throw new FaultValidationException(key + " must be text");
        return value.textValue();
    }
    private JsonNode required(FaultSpec fault, String key) {
        JsonNode value = fault.parameters().get(key);
        if (value == null) throw new FaultValidationException("fault " + fault.id() + " requires parameter " + key);
        return value;
    }
    private ExecutionDirective.Boundary boundary(FaultSpec fault) {
        try { return ExecutionDirective.Boundary.valueOf(text(fault, "boundary")); }
        catch (IllegalArgumentException exception) { throw new FaultValidationException("invalid worker crash boundary"); }
    }
    private String effectRationale(FaultSpec fault) {
        return switch (fault.type()) {
            case DUPLICATE -> "created " + integer(fault, "count") + " deterministic copies";
            case DROP -> "removed event from schedule";
            case DELAY -> "added " + longValue(fault, "durationMillis") + "ms logical delay";
            case REORDER -> "moved matching events to " + text(fault, "position");
            case RETRY_STORM -> "scheduled " + integer(fault, "retries") + " bounded retries";
            case DEPENDENCY_TIMEOUT -> "scripted dependency timeout";
            case WORKER_CRASH -> "scripted worker crash at " + text(fault, "boundary");
            case MALFORMED_PAYLOAD -> "applied isolated malformed payload/schema transformation";
        };
    }
}
