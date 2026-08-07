package dev.replayforge.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class VirtualClockTest {
    @Test void advancesOnlyWhenExplicitlyRequested() {
        VirtualClock clock = new VirtualClock(Instant.EPOCH);
        assertThat(clock.instant()).isEqualTo(Instant.EPOCH);
        assertThat(clock.advance(Duration.ofSeconds(3))).isEqualTo(Instant.EPOCH.plusSeconds(3));
        assertThatThrownBy(() -> clock.advance(Duration.ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
    }
}
