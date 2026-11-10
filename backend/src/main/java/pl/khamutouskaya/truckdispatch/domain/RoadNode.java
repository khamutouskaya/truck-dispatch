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

// Punkt na mapie: miasto lub węzeł drogowy (wierzchołek grafu dróg)
@Entity
@Table(name = "road_node")
@Getter
@Setter
@NoArgsConstructor
public class RoadNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "lat", nullable = false)
    private double lat;

    @Column(name = "lon", nullable = false)
    private double lon;

    // Domyślny czas załadunku/rozładunku w tym punkcie, minuty
    @Column(name = "default_handling_min", nullable = false)
    private int defaultHandlingMin = 60;
}
