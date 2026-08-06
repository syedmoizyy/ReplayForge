package dev.replayforge.domain.event;

import static dev.replayforge.support.EventFixture.event;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class DomainEventTest {
    @Test void rejectsNonPositiveSequence() {
        assertThatThrownBy(() -> event().sequence(0).build()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sequenceNumber");
    }
}
