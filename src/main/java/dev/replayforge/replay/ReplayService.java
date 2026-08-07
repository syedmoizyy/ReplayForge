package dev.replayforge.replay;

import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.eventstore.EventStore;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class ReplayService {
    private final EventStore eventStore;
    private final ReplayRunRepository repository;
    private final DeterministicReplayEngine engine;
    private final Executor executor;
    private final Clock systemClock;

    public ReplayService(EventStore eventStore, ReplayRunRepository repository, DeterministicReplayEngine engine,
            @Qualifier("replayExecutor") Executor executor, Clock systemClock) {
        this.eventStore = eventStore; this.repository = repository; this.engine = engine;
        this.executor = executor; this.systemClock = systemClock;
    }

    public ReplayRun start(UUID sourceCorrelationId, long checkpoint, long seed, ReplayRun.ClockMode clockMode) {
        List<DomainEvent> source = eventStore.findByCorrelationId(sourceCorrelationId);
        if (source.isEmpty()) throw new ReplayValidationException("No source trace found for correlationId " + sourceCorrelationId);
        UUID replayId = UUID.randomUUID();
        ReplayRun queued = new ReplayRun(replayId, sourceCorrelationId, checkpoint, seed, clockMode,
                ReplayRun.Status.QUEUED, systemClock.instant(), null, null, null, null, null);
        repository.create(queued);
        executor.execute(() -> execute(queued, source));
        return queued;
    }

    public ReplayRun get(UUID replayId) {
        return repository.find(replayId).orElseThrow(() -> new ReplayNotFoundException(replayId));
    }

    public ReplayTrace trace(UUID replayId) {
        get(replayId);
        return new ReplayTrace(repository.events(replayId), repository.decisions(replayId));
    }

    public ReplayState state(UUID replayId) {
        ReplayRun run = get(replayId);
        if (run.status() != ReplayRun.Status.COMPLETED) throw new ReplayValidationException("Replay is not complete: " + replayId);
        return run.finalState();
    }

    private void execute(ReplayRun run, List<DomainEvent> source) {
        repository.markRunning(run.replayId(), systemClock.instant());
        try {
            ReplayExecution output = engine.execute(source, run.checkpoint(), run.seed(), run.clockMode());
            repository.complete(run.replayId(), systemClock.instant(), output);
        } catch (RuntimeException error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            repository.fail(run.replayId(), systemClock.instant(), message);
        }
    }

    public record ReplayTrace(List<ReplayedEvent> events, List<ReplayDecision> decisions) {}
}
