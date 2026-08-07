package dev.replayforge.replay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.domain.event.EventEnvelopeSerializer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import dev.replayforge.invariants.InvariantViolation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ReplayRunRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final EventEnvelopeSerializer serializer;
    public ReplayRunRepository(JdbcTemplate jdbc, ObjectMapper mapper, EventEnvelopeSerializer serializer) {
        this.jdbc = jdbc; this.mapper = mapper; this.serializer = serializer;
    }

    public void create(ReplayRun run) {
        jdbc.update("""
            insert into replay_runs(replay_id,source_correlation_id,checkpoint,seed,clock_mode,status,created_at)
            values (?,?,?,?,?,?,?)
            """, run.replayId(), run.sourceCorrelationId(), run.checkpoint(), run.seed(), run.clockMode().name(),
                run.status().name(), Timestamp.from(run.createdAt()));
    }

    public void markRunning(UUID replayId, Instant startedAt) {
        jdbc.update("update replay_runs set status='RUNNING',started_at=? where replay_id=? and status='QUEUED'",
                Timestamp.from(startedAt), replayId);
    }

    @Transactional
    public void complete(UUID replayId, Instant completedAt, ReplayExecution execution) {
        for (ReplayedEvent replayed : execution.events()) {
            jdbc.update("insert into replay_events(replay_id,replay_order,source_event_id,replay_event_id,envelope) values (?,?,?,?,?::jsonb)",
                    replayId, replayed.order(), replayed.sourceEventId(), replayed.event().eventId(), serializer.serialize(replayed.event()));
        }
        for (ReplayDecision decision : execution.decisions()) {
            jdbc.update("""
                insert into replay_decisions(replay_id,decision_order,source_event_id,replay_event_id,decision_type,logical_time,detail)
                values (?,?,?,?,?,?,?::jsonb)
                """, replayId, decision.order(), decision.sourceEventId(), decision.replayEventId(), decision.type().name(),
                    Timestamp.from(decision.logicalTime()), json(decision.detail()));
        }
        jdbc.update("""
                update replay_runs set status='COMPLETED',completed_at=?,output_summary=?::jsonb,final_state=?::jsonb,
                violations=?::jsonb,divergence_report=?::jsonb,divergence_report_markdown=? where replay_id=?
                """, Timestamp.from(completedAt), json(execution.summary()), json(execution.finalState()),
                json(execution.violations()), execution.divergenceReportJson(), execution.divergenceReportMarkdown(), replayId);
    }

    public void fail(UUID replayId, Instant completedAt, String message) {
        jdbc.update("update replay_runs set status='FAILED',completed_at=?,error_message=? where replay_id=?",
                Timestamp.from(completedAt), message.substring(0, Math.min(message.length(), 1000)), replayId);
    }

    @Transactional(readOnly = true)
    public Optional<ReplayRun> find(UUID replayId) {
        return jdbc.query("select * from replay_runs where replay_id=?", this::mapRun, replayId).stream().findFirst();
    }

    public List<ReplayRun> findRecent(int limit) {
        return jdbc.query("select * from replay_runs order by created_at desc limit ?", this::mapRun, limit);
    }

    public List<InvariantViolation> violations(UUID replayId) {
        String value = jdbc.query("select violations::text from replay_runs where replay_id=?", (rs, row) -> rs.getString(1), replayId)
                .stream().findFirst().orElseThrow(() -> new ReplayNotFoundException(replayId));
        if (value == null) return List.of();
        try { return mapper.readValue(value, mapper.getTypeFactory().constructCollectionType(List.class, InvariantViolation.class)); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Invalid stored violations", error); }
    }

    @Transactional(readOnly = true)
    public List<ReplayedEvent> events(UUID replayId) {
        return jdbc.query("select replay_order,source_event_id,envelope::text from replay_events where replay_id=? order by replay_order",
                (rs, row) -> new ReplayedEvent(rs.getLong(1), rs.getObject(2, UUID.class), serializer.deserialize(rs.getString(3))), replayId);
    }

    @Transactional(readOnly = true)
    public List<ReplayDecision> decisions(UUID replayId) {
        return jdbc.query("select * from replay_decisions where replay_id=? order by decision_order", (rs, row) ->
                new ReplayDecision(rs.getLong("decision_order"), rs.getObject("source_event_id", UUID.class),
                        rs.getObject("replay_event_id", UUID.class), ReplayDecision.Type.valueOf(rs.getString("decision_type")),
                        rs.getTimestamp("logical_time").toInstant(), readMap(rs.getString("detail"))), replayId);
    }

    public ReplayService.ReplayReport report(UUID replayId) {
        return jdbc.query("select divergence_report::text,divergence_report_markdown from replay_runs where replay_id=?",
                (rs, row) -> new ReplayService.ReplayReport(rs.getString(1), rs.getString(2)), replayId).stream()
                .findFirst().orElseThrow(() -> new ReplayNotFoundException(replayId));
    }

    private ReplayRun mapRun(ResultSet rs, int row) throws SQLException {
        return new ReplayRun(rs.getObject("replay_id", UUID.class), rs.getObject("source_correlation_id", UUID.class),
                rs.getLong("checkpoint"), rs.getLong("seed"), ReplayRun.ClockMode.valueOf(rs.getString("clock_mode")),
                ReplayRun.Status.valueOf(rs.getString("status")), instant(rs, "created_at"), instant(rs, "started_at"),
                instant(rs, "completed_at"), read(rs.getString("output_summary"), ReplayOutputSummary.class),
                read(rs.getString("final_state"), ReplayState.class), rs.getString("error_message"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private <T> T read(String value, Class<T> type) throws SQLException {
        if (value == null) return null;
        try { return mapper.readValue(value, type); } catch (JsonProcessingException e) { throw new SQLException("Invalid replay JSON", e); }
    }
    @SuppressWarnings("unchecked") private java.util.Map<String,String> readMap(String value) throws SQLException {
        return read(value, java.util.Map.class);
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalArgumentException("Replay output is not serializable", e); }
    }
}
