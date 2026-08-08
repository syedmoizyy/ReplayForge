package dev.replayforge.replay;

import static org.assertj.core.api.Assertions.assertThat;

import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.support.EventFixture;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class ReplayDeterminismPropertyTest {
    @Test void determinismHoldsAcrossGeneratedSeedsAndTraceSizes() {
        Random cases = new Random(0x5eedL);
        for (int example = 0; example < 100; example++) {
            int size = 1 + cases.nextInt(40);
            long seed = cases.nextLong();
            List<DomainEvent> trace = trace(size);
            DeterministicReplayEngine engine = new DeterministicReplayEngine();

            ReplayExecution first = engine.execute(trace, 0, seed, ReplayRun.ClockMode.FIXED_EPOCH);
            ReplayExecution second = engine.execute(trace, 0, seed, ReplayRun.ClockMode.FIXED_EPOCH);
            assertThat(second.events()).as("size=%s seed=%s", size, seed).isEqualTo(first.events());
            assertThat(second.summary()).isEqualTo(first.summary());
            assertThat(second.divergenceReportJson()).isEqualTo(first.divergenceReportJson());
        }
    }

    private List<DomainEvent> trace(int size) {
        UUID aggregate = UUID.randomUUID();
        UUID correlation = UUID.randomUUID();
        return LongStream.rangeClosed(1, size).mapToObj(sequence -> EventFixture.event()
                .aggregateId(aggregate).correlationId(correlation).sequence(sequence).build()).toList();
    }
}
