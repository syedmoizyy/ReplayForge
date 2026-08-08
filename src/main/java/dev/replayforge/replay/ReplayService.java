package dev.replayforge.replay;

import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.eventstore.EventStore;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import dev.replayforge.invariants.InvariantViolation;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import dev.replayforge.config.ReplayExecutionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ReplayService {
    private static final Logger log = LoggerFactory.getLogger(ReplayService.class);
    private final EventStore eventStore;
    private final ReplayRunRepository repository;
    private final DeterministicReplayEngine engine;
    private final Executor executor;
    private final Clock systemClock;
    private final ReplayExecutionProperties properties;

    public ReplayService(EventStore eventStore, ReplayRunRepository repository, DeterministicReplayEngine engine,
            @Qualifier("replayExecutor") Executor executor, Clock systemClock, ReplayExecutionProperties properties) {
        this.eventStore = eventStore; this.repository = repository; this.engine = engine;
        this.executor = executor; this.systemClock = systemClock; this.properties = properties;
    }

    public ReplayRun start(UUID sourceCorrelationId, long checkpoint, long seed, ReplayRun.ClockMode clockMode) {
        List<DomainEvent> source = eventStore.findByCorrelationId(sourceCorrelationId);
        if (source.isEmpty()) throw new ReplayValidationException("No source trace found for correlationId " + sourceCorrelationId);
        if (source.size() > properties.maxSourceEvents()) throw new ReplayValidationException(
                "Source trace has " + source.size() + " events; limit is " + properties.maxSourceEvents());
        UUID replayId = UUID.randomUUID();
        ReplayRun queued = new ReplayRun(replayId, sourceCorrelationId, checkpoint, seed, clockMode,
                ReplayRun.Status.QUEUED, systemClock.instant(), null, null, null, null, null);
        repository.create(queued);
        try {
            executor.execute(() -> execute(queued, source));
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            repository.fail(replayId, systemClock.instant(), "Replay rejected because execution capacity is exhausted");
            log.warn("replay_rejected_capacity replayId={} correlationId={}", replayId, sourceCorrelationId);
            throw new ReplayCapacityException(properties.retryAfterSeconds());
        }
        log.info("replay_queued replayId={} correlationId={} sourceEvents={} checkpoint={} seed={}",
                replayId, sourceCorrelationId, source.size(), checkpoint, seed);
        return queued;
    }

    public ReplayRun get(UUID replayId) {
        return repository.find(replayId).orElseThrow(() -> new ReplayNotFoundException(replayId));
    }

    public List<ReplayRun> recent(int limit) { return repository.findRecent(limit); }
    public List<InvariantViolation> violations(UUID replayId) { get(replayId); return repository.violations(replayId); }

    public ReplayTrace trace(UUID replayId) {
        get(replayId);
        return new ReplayTrace(repository.events(replayId), repository.decisions(replayId));
    }

    public ReplayState state(UUID replayId) {
        ReplayRun run = get(replayId);
        if (run.status() != ReplayRun.Status.COMPLETED) throw new ReplayValidationException("Replay is not complete: " + replayId);
        return run.finalState();
    }

    public ReplayReport report(UUID replayId) {
        ReplayRun run = get(replayId);
        if (run.status() != ReplayRun.Status.COMPLETED) throw new ReplayValidationException("Replay is not complete: " + replayId);
        return repository.report(replayId);
    }

    private void execute(ReplayRun run, List<DomainEvent> source) {
        repository.markRunning(run.replayId(), systemClock.instant());
        log.info("replay_started replayId={} correlationId={} sourceEvents={}", run.replayId(), run.sourceCorrelationId(), source.size());
        try {
            ReplayExecution output = engine.execute(source, run.checkpoint(), run.seed(), run.clockMode());
            repository.complete(run.replayId(), systemClock.instant(), output);
            log.info("replay_completed replayId={} processedEvents={} violations={}", run.replayId(),
                    output.summary().sourceEventCount(), output.violations().size());
        } catch (RuntimeException error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            repository.fail(run.replayId(), systemClock.instant(), message);
            log.error("replay_failed replayId={} errorType={}", run.replayId(), error.getClass().getSimpleName(), error);
        }
    }

    public record ReplayTrace(List<ReplayedEvent> events, List<ReplayDecision> decisions) {}
    public record ReplayReport(String json, String markdown) {}
}
