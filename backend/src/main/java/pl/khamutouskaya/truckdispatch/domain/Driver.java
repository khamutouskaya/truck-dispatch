package pl.khamutouskaya.truckdispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "driver")
@Getter
@Setter
@NoArgsConstructor
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 50)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 50)
    private String lastName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DriverStatus status = DriverStatus.AVAILABLE;

    // Текущее положение может быть неизвестно — поэтому обёртки
    @Column(name = "current_lat")
    private Double currentLat;

    @Column(name = "current_lon")
    private Double currentLon;

    @Column(name = "location_updated_at")
    private LocalDateTime locationUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_source", length = 20)
    private LocationSource locationSource;

    // Счётчики времени вождения (Регламент ЕС 561/2006), минуты
    @Column(name = "driving_time_today_min", nullable = false)
    private int drivingTimeTodayMin = 0;

    @Column(name = "driving_time_week_min", nullable = false)
    private int drivingTimeWeekMin = 0;

    @Column(name = "continuous_driving_min", nullable = false)
    private int continuousDrivingMin = 0;
}
