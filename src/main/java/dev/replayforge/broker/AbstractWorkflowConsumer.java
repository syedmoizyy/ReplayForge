package dev.replayforge.broker;

import dev.replayforge.config.ReplayForgeProperties;
import dev.replayforge.config.WorkflowBrokerProperties;
import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.domain.event.EventEnvelopeSerializer;
import dev.replayforge.sampleworkload.WorkflowEngine;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

public abstract class AbstractWorkflowConsumer {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final String name;
    private final StringRedisTemplate redis;
    private final EventEnvelopeSerializer serializer;
    private final WorkflowEngine engine;
    private final WorkflowBrokerProperties properties;
    private final String consumerId;

    protected AbstractWorkflowConsumer(String name, StringRedisTemplate redis, EventEnvelopeSerializer serializer,
            WorkflowEngine engine, WorkflowBrokerProperties properties, ReplayForgeProperties instance) {
        this.name = name; this.redis = redis; this.serializer = serializer; this.engine = engine;
        this.properties = properties; this.consumerId = name + "-" + instance.instanceId();
    }

    public int pollOnce() {
        if (!ensureGroup()) return 0;
        List<MapRecord<String, Object, Object>> records = read(ReadOffset.from("0"));
        if (records == null || records.isEmpty()) records = read(ReadOffset.lastConsumed());
        if (records == null) return 0;
        records.forEach(this::process);
        return records.size();
    }

    protected void pollScheduled() {
        if (properties.consumersEnabled()) pollOnce();
    }

    private List<MapRecord<String, Object, Object>> read(ReadOffset offset) {
        return redis.opsForStream().read(Consumer.from(name, consumerId), StreamReadOptions.empty().count(10).block(Duration.ofMillis(10)),
                StreamOffset.create(properties.stream(), offset));
    }

    private boolean ensureGroup() {
        try {
            redis.opsForStream().createGroup(properties.stream(), ReadOffset.from("0"), name);
            return true;
        } catch (DataAccessException error) {
            if (error.getMessage() != null && error.getMessage().contains("BUSYGROUP")) return true;
            if (error.getMessage() != null && error.getMessage().contains("no such key")) return false;
            throw error;
        }
    }

    private void process(MapRecord<String, Object, Object> record) {
        Map<Object, Object> values = record.getValue();
        try {
            DomainEvent input = serializer.deserialize(String.valueOf(values.get("envelope")));
            DomainEvent output = engine.react(name, input);
            redis.opsForStream().acknowledge(name, record);
            log.info("workflow_event_processed consumer={} eventId={} outputEventId={}", name, input.eventId(), output == null ? null : output.eventId());
        } catch (RuntimeException error) {
            retryOrDeadLetter(record, values, error);
        }
    }

    private void retryOrDeadLetter(MapRecord<String, Object, Object> record, Map<Object, Object> values, RuntimeException error) {
        int attempt = Integer.parseInt(String.valueOf(values.getOrDefault("attempt", "1")));
        if (attempt < properties.maxAttempts()) {
            long backoff = Math.min(properties.initialBackoffMs() * (1L << Math.min(attempt - 1, 10)), 30_000L);
            try { Thread.sleep(backoff); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            redis.opsForStream().add(StreamRecords.newRecord().in(properties.stream()).ofMap(Map.of(
                    "eventId", String.valueOf(values.get("eventId")), "envelope", String.valueOf(values.get("envelope")),
                    "attempt", Integer.toString(attempt + 1), "retryOf", record.getId().getValue())));
        } else {
            redis.opsForStream().add(StreamRecords.newRecord().in(properties.deadLetterStream()).ofMap(Map.of(
                    "consumer", name, "eventId", String.valueOf(values.get("eventId")), "envelope", String.valueOf(values.get("envelope")),
                    "attempt", Integer.toString(attempt), "error", safeMessage(error))));
            log.error("workflow_event_dead_lettered consumer={} eventId={} attempts={}", name, values.get("eventId"), attempt, error);
        }
        redis.opsForStream().acknowledge(name, record);
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null ? error.getClass().getSimpleName() : message.substring(0, Math.min(message.length(), 500));
    }
}
