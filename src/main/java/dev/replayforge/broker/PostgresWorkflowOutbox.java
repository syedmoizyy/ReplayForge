package dev.replayforge.broker;

import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.domain.event.EventEnvelopeSerializer;
import dev.replayforge.sampleworkload.WorkflowOutbox;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresWorkflowOutbox implements WorkflowOutbox {
    public record Pending(UUID eventId, String envelope) {}
    private final JdbcTemplate jdbc;
    private final EventEnvelopeSerializer serializer;
    public PostgresWorkflowOutbox(JdbcTemplate jdbc, EventEnvelopeSerializer serializer) {
        this.jdbc = jdbc; this.serializer = serializer;
    }
    @Override public void enqueue(DomainEvent event) {
        jdbc.update("insert into workflow_outbox(event_id,envelope) values (?,?::jsonb) on conflict do nothing",
                event.eventId(), serializer.serialize(event));
    }
    public List<Pending> pending(int limit) {
        return jdbc.query("select event_id,envelope::text from workflow_outbox where published_at is null order by created_at limit ?",
                (rs, row) -> new Pending(rs.getObject(1, UUID.class), rs.getString(2)), limit);
    }
    public void markPublished(UUID eventId) {
        jdbc.update("update workflow_outbox set published_at=now() where event_id=? and published_at is null", eventId);
    }
}
