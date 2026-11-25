package pl.khamutouskaya.truckdispatch.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.WeightedCriterion;
import pl.khamutouskaya.truckdispatch.config.AssignmentWeightsProperties;
import pl.khamutouskaya.truckdispatch.domain.AssignmentWeights;
import pl.khamutouskaya.truckdispatch.dto.AssignmentWeightsRequest;
import pl.khamutouskaya.truckdispatch.dto.AssignmentWeightsResponse;
import pl.khamutouskaya.truckdispatch.repository.AssignmentWeightsRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentWeightsServiceTest {

    private static final AssignmentWeightsProperties DEFAULTS =
            new AssignmentWeightsProperties(0.25, 0.30, 0.15, 0.15, 0.15);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T10:00:00Z"), ZoneId.of("UTC"));

    @Mock private AssignmentWeightsRepository repository;
    private AssignmentWeightsService service;

    @BeforeEach
    void setUp() {
        service = new AssignmentWeightsService(repository, DEFAULTS, CLOCK);
    }

    private static AssignmentWeights saved(double k1, double k2, double k3, double k4, double k5) {
        AssignmentWeights entity = new AssignmentWeights();
        entity.setId(1L);
        entity.setDistanceToLoading(k1);
        entity.setTimeToLoading(k2);
        entity.setRemainingDrivingToday(k3);
        entity.setStopCount(k4);
        entity.setVehicleUtilization(k5);
        entity.setCreatedAt(LocalDateTime.of(2026, 11, 18, 9, 0));
        return entity;
    }

    @Test
    void usesDefaultsFromConfigurationWhenNothingWasSaved() {
        when(repository.findFirstByOrderByIdDesc()).thenReturn(Optional.empty());

        AssignmentWeightsResponse response = service.current();

        assertThat(response.isDefault()).isTrue();
        assertThat(response.updatedAt()).isNull();
        assertThat(response.current().timeToLoading()).isEqualTo(0.30);
        assertThat(service.currentCriteria()).extracting(WeightedCriterion::weight)
                .containsExactly(0.25, 0.30, 0.15, 0.15, 0.15);
    }

    @Test
    void usesTheNewestSavedWeightsInCriterionOrderK1ToK5() {
        when(repository.findFirstByOrderByIdDesc()).thenReturn(Optional.of(saved(0.1, 0.5, 0.2, 0.1, 0.1)));

        assertThat(service.currentCriteria()).extracting(WeightedCriterion::weight)
                .containsExactly(0.1, 0.5, 0.2, 0.1, 0.1);
        AssignmentWeightsResponse response = service.current();
        assertThat(response.isDefault()).isFalse();
        assertThat(response.defaults().timeToLoading()).isEqualTo(0.30);
    }

    @Test
    void savingAddsANewRowWithTheCurrentTime() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentWeightsResponse response =
                service.update(new AssignmentWeightsRequest(0.2, 0.2, 0.2, 0.2, 0.2));

        assertThat(response.current().stopCount()).isEqualTo(0.2);
        assertThat(response.updatedAt()).isEqualTo(LocalDateTime.of(2026, 11, 18, 10, 0));
        assertThat(response.isDefault()).isFalse();
    }

    @Test
    void resetSavesTheDefaultsSoTheyCountAsDefault() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AssignmentWeightsResponse response = service.resetToDefaults();

        assertThat(response.isDefault()).isTrue();
        assertThat(response.current()).isEqualTo(response.defaults());
    }
}
