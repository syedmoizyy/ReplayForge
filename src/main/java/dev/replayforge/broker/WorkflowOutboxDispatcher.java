package dev.replayforge.broker;

import dev.replayforge.config.WorkflowBrokerProperties;
import java.util.Map;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkflowOutboxDispatcher {
    private final PostgresWorkflowOutbox outbox;
    private final StringRedisTemplate redis;
    private final WorkflowBrokerProperties properties;
    public WorkflowOutboxDispatcher(PostgresWorkflowOutbox outbox, StringRedisTemplate redis, WorkflowBrokerProperties properties) {
        this.outbox = outbox; this.redis = redis; this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${replayforge.workflow-broker.poll-delay-ms:250}")
    public void dispatch() {
        if (properties.consumersEnabled()) dispatchOnce();
    }

    public int dispatchOnce() {
        int count = 0;
        for (PostgresWorkflowOutbox.Pending pending : outbox.pending(100)) {
            redis.opsForStream().add(StreamRecords.newRecord().in(properties.stream()).ofMap(Map.of(
                    "eventId", pending.eventId().toString(), "envelope", pending.envelope(), "attempt", "1")));
            outbox.markPublished(pending.eventId());
            count++;
        }
        return count;
    }
}
