package pl.khamutouskaya.truckdispatch.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.domain.AssignmentResult;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;
import pl.khamutouskaya.truckdispatch.repository.AssignmentResultRepository;
import pl.khamutouskaya.truckdispatch.repository.DriverRepository;
import pl.khamutouskaya.truckdispatch.repository.TransportOrderRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Test na prawdziwej bazie (migracja V7). @Transactional — każdy test wycofuje swoje zmiany.
@SpringBootTest
@Transactional
class AssignmentResultRepositoryIntegrationTest {

    @Autowired
    private AssignmentResultRepository assignmentResultRepository;

    @Autowired
    private TransportOrderRepository transportOrderRepository;

    @Autowired
    private DriverRepository driverRepository;

    private AssignmentResult result(TransportOrder order, Driver driver, Double score, LocalDateTime createdAt) {
        AssignmentResult result = new AssignmentResult();
        result.setOrder(order);
        result.setDriver(driver);
        result.setScore(score);
        result.setDetails("{\"weights\": {\"distanceToLoadingKm\": 0.25}, \"rejected\": [{\"reason\": \"TYPE\"}]}");
        result.setCreatedAt(createdAt);
        return result;
    }

    @Test
    void savesHistoryWithAndWithoutWinnerNewestFirst() {
        TransportOrder order = transportOrderRepository.findAll().getFirst();
        Driver driver = driverRepository.findAll().getFirst();
        LocalDateTime now = LocalDateTime.of(2026, 11, 11, 8, 0);

        assignmentResultRepository.saveAndFlush(result(order, null, null, now));            // nikt nie przeszedł
        assignmentResultRepository.saveAndFlush(result(order, driver, 0.85, now.plusHours(1))); // ponowne uruchomienie

        List<AssignmentResult> history = assignmentResultRepository.findByOrderIdOrderByCreatedAtDescIdDesc(order.getId());

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getDriver().getId()).isEqualTo(driver.getId());
        assertThat(history.get(0).getScore()).isEqualTo(0.85);
        assertThat(history.get(1).getDriver()).isNull();
        // JSONB zachowuje treść (PostgreSQL może zmienić odstępy i kolejność kluczy)
        assertThat(history.get(0).getDetails()).contains("\"reason\"").contains("TYPE");
    }

    @Test
    void databaseRejectsScoreWithoutDriver() {
        TransportOrder order = transportOrderRepository.findAll().getFirst();

        assertThatThrownBy(() -> assignmentResultRepository.saveAndFlush(
                result(order, null, 0.5, LocalDateTime.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsScoreOutsideZeroOne() {
        TransportOrder order = transportOrderRepository.findAll().getFirst();
        Driver driver = driverRepository.findAll().getFirst();

        assertThatThrownBy(() -> assignmentResultRepository.saveAndFlush(
                result(order, driver, 1.5, LocalDateTime.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
