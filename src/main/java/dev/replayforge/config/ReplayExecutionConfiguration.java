package dev.replayforge.config;

import java.time.Clock;
import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ReplayExecutionConfiguration {
    @Bean public Clock systemClock() { return Clock.systemUTC(); }
    @Bean(name = "replayExecutor") public Executor replayExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("replay-");
        executor.initialize();
        return executor;
    }
}
