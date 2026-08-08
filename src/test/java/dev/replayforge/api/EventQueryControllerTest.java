package dev.replayforge.api;

import static org.mockito.Mockito.verify;
import dev.replayforge.eventstore.EventStore;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.List;

@WebMvcTest(EventQueryController.class)
class EventQueryControllerTest {
    @Autowired MockMvc mvc;
    @MockBean EventStore store;

    @Test void queriesTraceByCorrelationId() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/traces/{id}", id)).andExpect(status().isOk());
        verify(store).findByCorrelationId(id);
    }

    @Test void queriesAggregateStream() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(get("/api/v1/aggregates/{id}/events", id)).andExpect(status().isOk());
        verify(store).findByAggregateId(id);
    }

    @Test void listsTraceSummariesUsingStableContract() throws Exception {
        UUID correlation = UUID.randomUUID(); UUID aggregate = UUID.randomUUID();
        when(store.findTraces(25)).thenReturn(List.of(new EventStore.TraceSummary(correlation, aggregate, 5,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:01Z"), "PayoutSent")));
        mvc.perform(get("/api/v1/traces").param("limit", "25")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].correlationId").value(correlation.toString()))
                .andExpect(jsonPath("$[0].eventCount").value(5))
                .andExpect(jsonPath("$[0].lastEventType").value("PayoutSent"));
    }
}
