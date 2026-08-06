package dev.replayforge.domain.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public final class EventEnvelopeSerializer {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private final ObjectMapper objectMapper;

    public EventEnvelopeSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(DomainEvent event) {
        requireSupported(event.schemaVersion());
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException("Could not serialize event " + event.eventId(), e);
        }
    }

    public DomainEvent deserialize(String value) {
        try {
            JsonNode tree = objectMapper.readTree(value);
            JsonNode version = tree.get("schemaVersion");
            if (version == null || !version.canConvertToInt()) {
                throw new EventSerializationException("Event envelope is missing an integer schemaVersion");
            }
            requireSupported(version.intValue());
            return objectMapper.treeToValue(tree, DomainEvent.class);
        } catch (EventSerializationException e) {
            throw e;
        } catch (JsonProcessingException e) {
            throw new EventSerializationException("Invalid event envelope JSON", e);
        }
    }

    private void requireSupported(int version) {
        if (version != CURRENT_SCHEMA_VERSION) {
            throw new EventSerializationException("Unsupported event schemaVersion " + version + "; supported versions: [1]");
        }
    }
}
