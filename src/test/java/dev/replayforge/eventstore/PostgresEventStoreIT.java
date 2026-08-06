package dev.replayforge.eventstore;

import static dev.replayforge.support.EventFixture.event;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import dev.replayforge.domain.event.DomainEvent;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "spring.data.redis.repositories.enabled=false")
@Testcontainers
class PostgresEventStoreIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired EventStore store;

    @Test void appendsAndQueriesInAggregateAndTraceOrder() {
        UUID aggregate = UUID.randomUUID(); UUID correlation = UUID.randomUUID();
        DomainEvent first = event().aggregateId(aggregate).correlationId(correlation).sequence(1).build();
        DomainEvent second = event().aggregateId(aggregate).correlationId(correlation).sequence(2).build();
        store.append(first); store.append(second);
        assertThat(store.findByAggregateId(aggregate)).containsExactly(first, second);
        assertThat(store.findByCorrelationId(correlation)).containsExactly(first, second);
    }

    @Test void duplicateEventIdIsRejected() {
        UUID eventId = UUID.randomUUID();
        store.append(event().eventId(eventId).build());
        assertThatThrownBy(() -> store.append(event().eventId(eventId).build())).isInstanceOf(DuplicateEventException.class);
    }

    @Test void sameIdempotentRequestReturnsOriginalButConflictingRequestFails() {
        UUID aggregate = UUID.randomUUID(); String key = "request-1";
        DomainEvent original = event().aggregateId(aggregate).idempotencyKey(key).build();
        store.append(original);
        DomainEvent retry = event().aggregateId(aggregate).correlationId(original.correlationId()).idempotencyKey(key).build();
        assertThat(store.append(retry)).isEqualTo(new AppendResult(AppendResult.Status.IDEMPOTENT_REPLAY, original));
        DomainEvent conflict = event().aggregateId(aggregate).idempotencyKey(key).build();
        assertThatThrownBy(() -> store.append(conflict)).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test void sequenceMustBeMonotonic() {
        assertThatThrownBy(() -> store.append(event().sequence(2).build())).isInstanceOf(SequenceConflictException.class)
                .hasMessageContaining("Expected sequenceNumber 1");
    }

    @Test void concurrentWritersCannotBothClaimTheSameSequence() throws Exception {
        UUID aggregate = UUID.randomUUID(); CountDownLatch start = new CountDownLatch(1);
        DomainEvent left = event().aggregateId(aggregate).sequence(1).build();
        DomainEvent right = event().aggregateId(aggregate).sequence(1).build();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Object> a = executor.submit(() -> appendAfter(start, left));
            Future<Object> b = executor.submit(() -> appendAfter(start, right));
            start.countDown();
            assertThat(java.util.List.of(a.get(), b.get())).anyMatch(AppendResult.class::isInstance)
                    .anyMatch(SequenceConflictException.class::isInstance);
        }
        assertThat(store.findByAggregateId(aggregate)).hasSize(1);
    }

    private Object appendAfter(CountDownLatch start, DomainEvent value) throws InterruptedException {
        start.await();
        try { return store.append(value); } catch (SequenceConflictException error) { return error; }
    }
}
