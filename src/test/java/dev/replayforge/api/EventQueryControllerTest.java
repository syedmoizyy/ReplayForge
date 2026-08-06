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
}
