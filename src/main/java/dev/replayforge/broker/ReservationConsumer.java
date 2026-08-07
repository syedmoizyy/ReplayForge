package dev.replayforge.broker;
import dev.replayforge.config.*; import dev.replayforge.domain.event.EventEnvelopeSerializer; import dev.replayforge.sampleworkload.WorkflowEngine;
import org.springframework.data.redis.core.StringRedisTemplate; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;
@Component public final class ReservationConsumer extends AbstractWorkflowConsumer {
 public ReservationConsumer(StringRedisTemplate r, EventEnvelopeSerializer s, WorkflowEngine e, WorkflowBrokerProperties p, ReplayForgeProperties i){super("reservation",r,s,e,p,i);}
 @Scheduled(fixedDelayString="${replayforge.workflow-broker.poll-delay-ms:250}") public void poll(){pollScheduled();}
}
