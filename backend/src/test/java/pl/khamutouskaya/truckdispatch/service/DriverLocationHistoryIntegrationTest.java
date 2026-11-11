package pl.khamutouskaya.truckdispatch.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.domain.DriverLocation;
import pl.khamutouskaya.truckdispatch.domain.LocationSource;
import pl.khamutouskaya.truckdispatch.dto.DriverLocationRequest;
import pl.khamutouskaya.truckdispatch.repository.DriverLocationRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Ręczna zmiana położenia kierowcy zapisuje punkt w historii driver_location (migracja V8)
@SpringBootTest
@Transactional
class DriverLocationHistoryIntegrationTest {

    @Autowired private DriverService driverService;
    @Autowired private DriverLocationRepository driverLocationRepository;

    @Test
    void manualLocationUpdateIsRecordedInHistory() {
        long before = driverLocationRepository.count();

        driverService.updateLocation(1L, new DriverLocationRequest(51.7592, 19.4560)); // Łódź

        List<DriverLocation> all = driverLocationRepository.findAll();
        assertThat(all).hasSize((int) before + 1);
        DriverLocation last = all.getLast();
        assertThat(last.getDriver().getId()).isEqualTo(1L);
        assertThat(last.getLat()).isEqualTo(51.7592);
        assertThat(last.getSource()).isEqualTo(LocationSource.MANUAL);
        assertThat(last.getOrder()).isNull();
        assertThat(last.getRecordedAt()).isNotNull();
    }
}
