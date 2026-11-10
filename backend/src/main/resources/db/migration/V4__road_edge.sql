-- Krawędź grafu dróg: odcinek drogi w JEDNYM kierunku między dwoma sąsiednimi węzłami.
-- Jedna droga = dwa wiersze (tam i z powrotem).
-- Czasu przejazdu nie przechowujemy: liczy się go wzorem dla konkretnego pojazdu (pusty / załadowany).
CREATE TABLE road_edge (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    from_node_id       BIGINT           NOT NULL REFERENCES road_node (id),
    to_node_id         BIGINT           NOT NULL REFERENCES road_node (id),
    distance_km        DOUBLE PRECISION NOT NULL CHECK (distance_km > 0),
    road_type          VARCHAR(20)      NOT NULL
        CHECK (road_type IN ('MOTORWAY', 'EXPRESSWAY', 'NATIONAL', 'REGIONAL', 'LOCAL')),
    surface_quality    VARCHAR(20)      NOT NULL DEFAULT 'GOOD'
        CHECK (surface_quality IN ('GOOD', 'AVERAGE', 'POOR')),
    terrain            VARCHAR(20)      NOT NULL DEFAULT 'FLAT'
        CHECK (terrain IN ('FLAT', 'HILLY', 'MOUNTAIN')),
    -- NULL = brak ograniczenia
    max_weight_t       DOUBLE PRECISION CHECK (max_weight_t > 0),
    max_height_m       DOUBLE PRECISION CHECK (max_height_m > 0),
    -- Poprawka uczona po zrealizowanych zleceniach (1.0 = zgodnie z prognozą)
    delay_factor       DOUBLE PRECISION NOT NULL DEFAULT 1.0 CHECK (delay_factor > 0),
    -- Linia drogi do rysowania na mapie: tablica punktów [[lon, lat], ...] (kolejność jak w GeoJSON)
    geometry           JSONB,
    -- Czas wzorcowy z openrouteservice, minuty; tylko do kalibracji współczynników
    reference_time_min INTEGER CHECK (reference_time_min > 0),
    CHECK (from_node_id <> to_node_id),
    UNIQUE (from_node_id, to_node_id)
);

-- Graf jest ładowany w całości, ale indeks przyspiesza wyszukiwanie krawędzi wychodzących z węzła
CREATE INDEX idx_road_edge_from_node ON road_edge (from_node_id);
