package dev.replayforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ReplayForgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReplayForgeApplication.class, args);
    }
}
