package pl.khamutouskaya.truckdispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Wagi kryteriów K1–K5 zapisane przez dyspozytora. Wiersze tylko dopisujemy (historia), obowiązuje najnowszy.
 */
@Entity
@Table(name = "assignment_weights")
@Getter
@Setter
@NoArgsConstructor
public class AssignmentWeights {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "distance_to_loading", nullable = false)
    private double distanceToLoading;          // K1

    @Column(name = "time_to_loading", nullable = false)
    private double timeToLoading;              // K2

    @Column(name = "remaining_driving_today", nullable = false)
    private double remainingDrivingToday;      // K3

    @Column(name = "stop_count", nullable = false)
    private double stopCount;                  // K4

    @Column(name = "vehicle_utilization", nullable = false)
    private double vehicleUtilization;         // K5

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
