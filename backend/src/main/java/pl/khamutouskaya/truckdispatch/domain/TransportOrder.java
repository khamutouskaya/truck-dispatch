package pl.khamutouskaya.truckdispatch.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

// Zlecenie transportowe (Order to słowo zastrzeżone w SQL, dlatego TransportOrder)
@Entity
@Table(name = "transport_order")
@Getter
@Setter
@NoArgsConstructor
public class TransportOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "loading_node_id", nullable = false)
    private RoadNode loadingNode;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "unloading_node_id", nullable = false)
    private RoadNode unloadingNode;

    @Column(name = "cargo_weight_kg", nullable = false)
    private int cargoWeightKg;

    @Column(name = "cargo_volume_m3", nullable = false)
    private double cargoVolumeM3;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_vehicle_type", nullable = false, length = 20)
    private VehicleType requiredVehicleType;

    @Column(name = "loading_deadline", nullable = false)
    private LocalDateTime loadingDeadline;

    @Column(name = "delivery_deadline", nullable = false)
    private LocalDateTime deliveryDeadline;

    // Jeśli nie podano — przyjmowany jest default_handling_min z RoadNode
    @Column(name = "estimated_loading_min")
    private Integer estimatedLoadingMin;

    @Column(name = "estimated_unloading_min")
    private Integer estimatedUnloadingMin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.NEW;

    // Dopóki zlecenie ma status NEW — nie ma kierowcy
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_driver_id")
    private Driver assignedDriver;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
