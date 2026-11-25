-- Wagi kryteriów K1–K5 ustawione przez dyspozytora na stronie „Wagi”.
-- Każdy zapis to nowy wiersz (historia zmian); obowiązuje najnowszy.
-- Pusta tabela = wagi domyślne z application.yaml (assignment.weights.*).
CREATE TABLE assignment_weights (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    distance_to_loading     DOUBLE PRECISION NOT NULL CHECK (distance_to_loading BETWEEN 0 AND 1),     -- K1
    time_to_loading         DOUBLE PRECISION NOT NULL CHECK (time_to_loading BETWEEN 0 AND 1),         -- K2
    remaining_driving_today DOUBLE PRECISION NOT NULL CHECK (remaining_driving_today BETWEEN 0 AND 1), -- K3
    stop_count              DOUBLE PRECISION NOT NULL CHECK (stop_count BETWEEN 0 AND 1),              -- K4
    vehicle_utilization     DOUBLE PRECISION NOT NULL CHECK (vehicle_utilization BETWEEN 0 AND 1),     -- K5
    created_at              TIMESTAMP        NOT NULL
);
