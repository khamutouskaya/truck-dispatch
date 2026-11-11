package pl.khamutouskaya.truckdispatch.algorithm.worktime;

/**
 * Postój wstawiony w trakcie jednego wywołania {@link DrivingClock#drive}.
 *
 * @param type            przerwa albo odpoczynek
 * @param startMin        początek postoju [min od startu zegara]
 * @param drivenBeforeMin ile minut tego odcinka przejechano przed postojem — pozwala wskazać miejsce na drodze
 */
public record Stop(StopType type, double startMin, double drivenBeforeMin) {
}
