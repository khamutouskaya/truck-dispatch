package pl.khamutouskaya.truckdispatch.domain;

// Ukształtowanie terenu; współczynnik k_terrain zależy też od tego, czy pojazd jest załadowany
public enum Terrain {
    FLAT,      // 1.0
    HILLY,     // 0.9 (załadowany 0.85)
    MOUNTAIN   // 0.75 (załadowany 0.65)
}
