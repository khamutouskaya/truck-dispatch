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

/**
 * Jedna krawędź zaplanowanej trasy zlecenia (zapis w chwili przydziału).
 */
@Entity
@Table(name = "order_route_edge")
@Getter
@Setter
@NoArgsConstructor
public class OrderRouteEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private TransportOrder order;

    // Kolejność w całej trasie (EMPTY, potem LOADED), od 0
    @Column(name = "seq", nullable = false)
    private int seq;

    @Enumerated(EnumType.STRING)
    @Column(name = "leg", nullable = false, length = 10)
    private RouteLeg leg;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "edge_id", nullable = false)
    private RoadEdge edge;

    // Prognoza z delayFactor z chwili przydziału [min]
    @Column(name = "planned_time_min", nullable = false)
    private double plannedTimeMin;

    // Prognoza bez delayFactor [min] — do samouczenia
    @Column(name = "base_time_min", nullable = false)
    private double baseTimeMin;
}
