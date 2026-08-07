package dev.replayforge.sampleworkload;

import static org.assertj.core.api.Assertions.assertThat;

import dev.replayforge.broker.PaymentConsumer;
import dev.replayforge.broker.PayoutConsumer;
import dev.replayforge.broker.RefundConsumer;
import dev.replayforge.broker.ReservationConsumer;
import dev.replayforge.broker.WorkflowOutboxDispatcher;
import dev.replayforge.config.WorkflowBrokerProperties;
import dev.replayforge.domain.event.DomainEvent;
import dev.replayforge.domain.event.EventEnvelopeSerializer;
import dev.replayforge.domain.workflow.ReservationProjection.PayoutStatus;
import dev.replayforge.domain.workflow.ReservationProjection.RefundStatus;
import dev.replayforge.domain.workflow.ReservationProjection.Status;
import dev.replayforge.eventstore.EventStore;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {"replayforge.instance-id=test", "replayforge.workflow-broker.consumers-enabled=false"})
@Testcontainers
class SampleWorkflowIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    @DynamicPropertySource static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired WorkflowEngine engine;
    @Autowired WorkflowOutboxDispatcher dispatcher;
    @Autowired ReservationConsumer reservation;
    @Autowired PaymentConsumer payment;
    @Autowired RefundConsumer refund;
    @Autowired PayoutConsumer payout;
    @Autowired EventStore eventStore;
    @Autowired StringRedisTemplate redis;
    @Autowired EventEnvelopeSerializer serializer;
    @Autowired WorkflowBrokerProperties broker;

    @Test void happyPathReachesSentPayout() {
        DomainEvent created = engine.start(2500, "USD", true, "happy-" + java.util.UUID.randomUUID());
        drainAll(12);
        var state = engine.state(created.aggregateId());
        assertThat(state.status()).isEqualTo(Status.CONFIRMED);
        assertThat(state.payoutStatus()).isEqualTo(PayoutStatus.SENT);
        assertThat(eventStore.findByAggregateId(created.aggregateId())).extracting(DomainEvent::eventType)
                .containsExactly("ReservationCreated", "DepositAuthorized", "ReservationConfirmed", "PayoutScheduled", "PayoutSent");
    }

    @Test void confirmedReservationCanBeCancelledAndRefunded() {
        DomainEvent created = engine.start(4000, "USD", false, "cancel-" + java.util.UUID.randomUUID());
        drainAll(8);
        assertThat(engine.state(created.aggregateId()).status()).isEqualTo(Status.CONFIRMED);
        engine.cancel(created.aggregateId(), "cancel-confirmed-" + created.eventId());
        drainAll(8);
        var state = engine.state(created.aggregateId());
        assertThat(state.status()).isEqualTo(Status.CANCELLED);
        assertThat(state.refundStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(state.payoutStatus()).isEqualTo(PayoutStatus.NONE);
    }

    @Test void duplicateRedisDeliveryDoesNotDuplicateBusinessEvents() {
        DomainEvent created = engine.start(1800, "USD", false, "duplicate-" + java.util.UUID.randomUUID());
        drainAll(8);
        int before = eventStore.findByAggregateId(created.aggregateId()).size();
        redis.opsForStream().add(StreamRecords.newRecord().in(broker.stream()).ofMap(Map.of(
                "eventId", created.eventId().toString(), "envelope", serializer.serialize(created), "attempt", "1")));
        drainAll(4);
        assertThat(eventStore.findByAggregateId(created.aggregateId())).hasSize(before);
    }

    @Test void stableConsumerRecoversItsPendingEntryAfterRestart() {
        DomainEvent created = engine.start(3200, "USD", false, "restart-" + java.util.UUID.randomUUID());
        dispatcher.dispatchOnce();
        try { redis.opsForStream().createGroup(broker.stream(), ReadOffset.from("0"), "restart-probe"); }
        catch (RuntimeException ignored) { }
        var claimed = redis.opsForStream().read(Consumer.from("restart-probe", "restart-probe-test"),
                StreamReadOptions.empty().count(1), StreamOffset.create(broker.stream(), ReadOffset.lastConsumed()));
        assertThat(claimed).hasSize(1);
        var recovered = redis.opsForStream().read(Consumer.from("restart-probe", "restart-probe-test"),
                StreamReadOptions.empty().count(1), StreamOffset.create(broker.stream(), ReadOffset.from("0")));
        assertThat(recovered).hasSize(1);
        redis.opsForStream().acknowledge("restart-probe", recovered.getFirst());

        payment.pollOnce();
        assertThat(eventStore.findByAggregateId(created.aggregateId())).extracting(DomainEvent::eventType)
                .contains("DepositAuthorized");
    }

    private void drainAll(int rounds) {
        for (int i = 0; i < rounds; i++) {
            dispatcher.dispatchOnce();
            payment.pollOnce(); reservation.pollOnce(); refund.pollOnce(); payout.pollOnce();
        }
    }
}
