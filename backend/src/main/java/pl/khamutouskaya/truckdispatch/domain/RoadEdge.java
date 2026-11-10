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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// Krawędź grafu dróg: odcinek w jednym kierunku (jedna droga = dwie krawędzie)
@Entity
@Table(name = "road_edge")
@Getter
@Setter
@NoArgsConstructor
public class RoadEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id", nullable = false)
    private RoadNode fromNode;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id", nullable = false)
    private RoadNode toNode;

    @Column(name = "distance_km", nullable = false)
    private double distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(name = "road_type", nullable = false, length = 20)
    private RoadType roadType;

    @Enumerated(EnumType.STRING)
    @Column(name = "surface_quality", nullable = false, length = 20)
    private SurfaceQuality surfaceQuality = SurfaceQuality.GOOD;

    @Enumerated(EnumType.STRING)
    @Column(name = "terrain", nullable = false, length = 20)
    private Terrain terrain = Terrain.FLAT;

    // null = brak ograniczenia
    @Column(name = "max_weight_t")
    private Double maxWeightT;

    @Column(name = "max_height_m")
    private Double maxHeightM;

    @Column(name = "delay_factor", nullable = false)
    private double delayFactor = 1.0;

    // Tekst JSON: [[lon, lat], ...]; przekazywany bez zmian do frontendu
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "geometry", columnDefinition = "jsonb")
    private String geometry;

    @Column(name = "reference_time_min")
    private Integer referenceTimeMin;
}
