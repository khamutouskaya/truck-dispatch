package pl.khamutouskaya.truckdispatch.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({AssignmentWeightsProperties.class, SimulatorProperties.class})
public class AssignmentConfig {

    // „Teraz” dla algorytmu przydziału; jako bean — w testach można podmienić na stały zegar
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
