-- Zaplanowana trasa przydzielonego zlecenia — zapisywana w chwili przydziału.
-- Potrzebna symulatorowi (po czym jedzie kierowca) i samouczeniu (z czym porównać czas rzeczywisty).
-- Pierwsza mila (pozycja kierowcy → pierwszy węzeł) nie jest krawędzią grafu, więc tu jej nie ma.
CREATE TABLE order_route_edge (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id         BIGINT           NOT NULL REFERENCES transport_order (id),
    -- Kolejność krawędzi w całej trasie: najpierw odcinek EMPTY, potem LOADED
    seq              INTEGER          NOT NULL CHECK (seq >= 0),
    -- EMPTY: dojazd pustym do załadunku; LOADED: załadunek → rozładunek
    leg              VARCHAR(10)      NOT NULL CHECK (leg IN ('EMPTY', 'LOADED')),
    edge_id          BIGINT           NOT NULL REFERENCES road_edge (id),
    -- Prognoza czasu krawędzi dla tego pojazdu w chwili przydziału, z ówczesnym delay_factor [min]
    planned_time_min DOUBLE PRECISION NOT NULL CHECK (planned_time_min > 0),
    -- To samo bez delay_factor [min] — mianownik w samouczeniu (fakt / czas bazowy)
    base_time_min    DOUBLE PRECISION NOT NULL CHECK (base_time_min > 0),
    UNIQUE (order_id, seq)
);

-- Historia położeń kierowcy: z symulatora, ręcznie od dyspozytora, w przyszłości z telefonu.
-- Z niej (rzutowanie na trasę) liczony jest rzeczywisty czas przejazdu krawędzi.
CREATE TABLE driver_location (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    driver_id   BIGINT           NOT NULL REFERENCES driver (id),
    -- Zlecenie realizowane w chwili pomiaru; NULL — kierowca bez zlecenia (np. ręczna zmiana pozycji)
    order_id    BIGINT REFERENCES transport_order (id),
    lat         DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lon         DOUBLE PRECISION NOT NULL CHECK (lon BETWEEN -180 AND 180),
    recorded_at TIMESTAMP        NOT NULL,
    source      VARCHAR(20)      NOT NULL CHECK (source IN ('MANUAL', 'PHONE', 'SIMULATION'))
);

CREATE INDEX idx_driver_location_order ON driver_location (order_id, recorded_at);
CREATE INDEX idx_driver_location_driver ON driver_location (driver_id, recorded_at);
