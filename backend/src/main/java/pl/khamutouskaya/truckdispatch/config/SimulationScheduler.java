package pl.khamutouskaya.truckdispatch.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import pl.khamutouskaya.truckdispatch.service.SimulationService;

// Tylko gdy simulator.enabled = true: co tickMs przesuwa wszystkie aktywne zlecenia o jeden krok czasu
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "simulator", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SimulationScheduler {

    private final SimulationService simulationService;
    private final SimulatorProperties properties;

    @Scheduled(fixedDelayString = "${simulator.tick-ms}")
    public void tick() {
        simulationService.advance(properties.stepMin());
    }
}
