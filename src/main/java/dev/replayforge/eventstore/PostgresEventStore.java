package dev.replayforge.eventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.domain.event.EventEnvelopeSerializer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresEventStore implements EventStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final EventEnvelopeSerializer envelopeSerializer;
    private final RowMapper<DomainEvent> rowMapper = this::mapEvent;

    public PostgresEventStore(JdbcTemplate jdbc, ObjectMapper mapper, EventEnvelopeSerializer envelopeSerializer) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.envelopeSerializer = envelopeSerializer;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppendResult append(DomainEvent event) {
        envelopeSerializer.serialize(event);
        jdbc.query("select pg_advisory_xact_lock(hashtext(?))", ignored -> { }, event.aggregateId().toString());

        Optional<DomainEvent> sameId = one("select * from events where event_id = ?", event.eventId());
        if (sameId.isPresent()) throw new DuplicateEventException("eventId already exists: " + event.eventId());

        Optional<DomainEvent> priorRequest = one("select * from events where aggregate_id = ? and idempotency_key = ?",
                event.aggregateId(), event.idempotencyKey());
        if (priorRequest.isPresent()) {
            DomainEvent prior = priorRequest.get();
            if (equivalentRequest(prior, event)) return new AppendResult(AppendResult.Status.IDEMPOTENT_REPLAY, prior);
            throw new IdempotencyConflictException("idempotencyKey already used with different event data: " + event.idempotencyKey());
        }

        Long current = jdbc.queryForObject("select coalesce(max(sequence_number), 0) from events where aggregate_id = ?", Long.class, event.aggregateId());
        long expected = current + 1;
        if (event.sequenceNumber() != expected) {
            throw new SequenceConflictException("Expected sequenceNumber " + expected + " for aggregate " + event.aggregateId() + " but got " + event.sequenceNumber());
        }

        try {
            jdbc.update("""
                    insert into events(event_id,event_type,schema_version,aggregate_id,correlation_id,causation_id,idempotency_key,
                      sequence_number,occurred_at,recorded_at,payload,metadata)
                    values (?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb)
                    """, event.eventId(), event.eventType(), event.schemaVersion(), event.aggregateId(), event.correlationId(),
                    event.causationId(), event.idempotencyKey(), event.sequenceNumber(), Timestamp.from(event.occurredAt()),
                    Timestamp.from(event.recordedAt()), json(event.payload()), json(event.metadata()));
        } catch (DuplicateKeyException e) {
            throw new SequenceConflictException("Concurrent append conflicted for aggregate " + event.aggregateId());
        }
        return new AppendResult(AppendResult.Status.APPENDED, event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomainEvent> findByAggregateId(UUID id) {
        return jdbc.query("select * from events where aggregate_id = ? order by sequence_number", rowMapper, id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DomainEvent> findByCorrelationId(UUID id) {
        return jdbc.query("select * from events where correlation_id = ? order by insertion_order", rowMapper, id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TraceSummary> findTraces(int limit) {
        return jdbc.query("""
                select correlation_id, min(aggregate_id::text)::uuid aggregate_id, count(*) event_count,
                  min(recorded_at) first_recorded_at, max(recorded_at) last_recorded_at,
                  (array_agg(event_type order by insertion_order desc))[1] last_event_type
                from events group by correlation_id order by max(recorded_at) desc limit ?
                """, (rs, row) -> new TraceSummary(rs.getObject("correlation_id", UUID.class),
                rs.getObject("aggregate_id", UUID.class), rs.getLong("event_count"),
                rs.getTimestamp("first_recorded_at").toInstant(), rs.getTimestamp("last_recorded_at").toInstant(),
                rs.getString("last_event_type")), limit);
    }

    private Optional<DomainEvent> one(String sql, Object... args) {
        return jdbc.query(sql, rowMapper, args).stream().findFirst();
    }

    private boolean equivalentRequest(DomainEvent a, DomainEvent b) {
        return a.eventType().equals(b.eventType()) && a.schemaVersion() == b.schemaVersion()
                && a.aggregateId().equals(b.aggregateId()) && a.correlationId().equals(b.correlationId())
                && java.util.Objects.equals(a.causationId(), b.causationId()) && a.sequenceNumber() == b.sequenceNumber()
                && a.payload().equals(b.payload()) && a.metadata().equals(b.metadata());
    }

    private DomainEvent mapEvent(ResultSet rs, int row) throws SQLException {
        try {
            return new DomainEvent(rs.getObject("event_id", UUID.class), rs.getString("event_type"), rs.getInt("schema_version"),
                    rs.getObject("aggregate_id", UUID.class), rs.getObject("correlation_id", UUID.class),
                    rs.getObject("causation_id", UUID.class), rs.getString("idempotency_key"), rs.getLong("sequence_number"),
                    rs.getTimestamp("occurred_at").toInstant(), rs.getTimestamp("recorded_at").toInstant(),
                    mapper.readTree(rs.getString("payload")), mapper.readValue(rs.getString("metadata"), new TypeReference<Map<String,String>>() {}));
        } catch (JsonProcessingException e) {
            throw new SQLException("Stored event JSON is invalid", e);
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Event JSON is not serializable", e); }
    }
}
