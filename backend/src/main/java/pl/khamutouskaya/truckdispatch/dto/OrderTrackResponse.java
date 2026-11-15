package pl.khamutouskaya.truckdispatch.dto;

import pl.khamutouskaya.truckdispatch.domain.LocationSource;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ślad GPS zlecenia (driver_location z order_id) — linia faktycznie przejechanej trasy na mapie.
 * To z tych punktów samouczenie liczy rzeczywiste czasy przejazdu krawędzi.
 *
 * @param driverId kierowca zlecenia albo null (zlecenie NEW / bez kierowcy)
 * @param points   punkty w kolejności czasu; pusta lista, jeśli podróż się jeszcze nie zaczęła
 */
public record OrderTrackResponse(
        long orderId,
        OrderStatus status,
        Long driverId,
        List<TrackPoint> points
) {

    /**
     * @param recordedAt czas pomiaru; dla symulacji — czas symulacji (przy speedup &gt; 1 wyprzedza zegar ścienny)
     */
    public record TrackPoint(double lat, double lon, LocalDateTime recordedAt, LocationSource source) {
    }
}
