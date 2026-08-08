package dev.replayforge.replay;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.replayforge.config.ReplayExecutionProperties;
import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.eventstore.EventStore;
import dev.replayforge.support.EventFixture;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class ReplayServiceTest {
    private final EventStore events = mock(EventStore.class);
    private final ReplayRunRepository repository = mock(ReplayRunRepository.class);
    private final DeterministicReplayEngine engine = mock(DeterministicReplayEngine.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test void rejectedExecutionIsPersistedAsFailedAndReportedAsCapacityError() {
        UUID correlationId = UUID.randomUUID();
        DomainEvent event = EventFixture.event().correlationId(correlationId).build();
        when(events.findByCorrelationId(correlationId)).thenReturn(List.of(event));
        Executor rejecting = task -> { throw new RejectedExecutionException("full"); };
        ReplayService service = new ReplayService(events, repository, engine, rejecting, clock, properties(10));

        assertThatThrownBy(() -> service.start(correlationId, 0, 1, ReplayRun.ClockMode.FIXED_EPOCH))
                .isInstanceOf(ReplayCapacityException.class).hasMessageContaining("retry after 5 seconds");
        verify(repository).create(any(ReplayRun.class));
        verify(repository).fail(any(UUID.class), any(Instant.class), any(String.class));
    }

    @Test void sourceSizeLimitRejectsBeforeCreatingAReplay() {
        UUID correlationId = UUID.randomUUID();
        when(events.findByCorrelationId(correlationId)).thenReturn(List.of(
                EventFixture.event().correlationId(correlationId).build(),
                EventFixture.event().correlationId(correlationId).build()));
        ReplayService service = new ReplayService(events, repository, engine, Runnable::run, clock, properties(1));

        assertThatThrownBy(() -> service.start(correlationId, 0, 1, ReplayRun.ClockMode.FIXED_EPOCH))
                .isInstanceOf(ReplayValidationException.class).hasMessageContaining("limit is 1");
    }

    private ReplayExecutionProperties properties(int maxSourceEvents) {
        return new ReplayExecutionProperties(1, 2, 1, maxSourceEvents, 5);
    }
}
