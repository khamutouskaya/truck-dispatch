package pl.khamutouskaya.truckdispatch.algorithm.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PolylineTest {

    // Łamana wzdłuż południka 20°E: 0,1° + 0,3° szerokości = 11,12 km + 33,36 km
    private static final List<GeoPoint> LINE = List.of(
            new GeoPoint(52.0, 20.0), new GeoPoint(52.1, 20.0), new GeoPoint(52.4, 20.0));

    @Test
    void lengthIsSumOfSegments() {
        assertThat(Polyline.lengthKm(LINE)).isCloseTo(4 * 11.11949, within(1e-3));
    }

    @Test
    void pointAtFractionOfLengthNotOfVertexCount() {
        // Połowa długości (0,2° od startu) leży w drugim odcinku, choć to „pierwszy z dwóch” wierzchołków środka
        assertThat(Polyline.pointAt(LINE, 0.5).lat()).isCloseTo(52.2, within(1e-6));
        assertThat(Polyline.pointAt(LINE, 0.25).lat()).isCloseTo(52.1, within(1e-6));
        assertThat(Polyline.pointAt(LINE, 0)).isEqualTo(LINE.getFirst());
        assertThat(Polyline.pointAt(LINE, 1)).isEqualTo(LINE.getLast());
        assertThat(Polyline.pointAt(LINE, 1.7)).isEqualTo(LINE.getLast()); // poza zakresem — przycięte
    }
}
