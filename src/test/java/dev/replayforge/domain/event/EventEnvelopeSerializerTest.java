package dev.replayforge.domain.event;

import static dev.replayforge.support.EventFixture.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

class EventEnvelopeSerializerTest {
    private final EventEnvelopeSerializer serializer = new EventEnvelopeSerializer(new ObjectMapper().registerModule(new JavaTimeModule()));

    @Test void roundTripsCurrentEnvelope() {
        DomainEvent original = event().build();
        assertThat(serializer.deserialize(serializer.serialize(original))).isEqualTo(original);
    }

    @Test void rejectsUnknownVersionWithUsefulMessage() {
        String json = serializer.serialize(event().build()).replace("\"schemaVersion\":1", "\"schemaVersion\":99");
        assertThatThrownBy(() -> serializer.deserialize(json)).isInstanceOf(EventSerializationException.class)
                .hasMessageContaining("Unsupported event schemaVersion 99");
    }
}
