-- Historia decyzji algorytmu przydziału: jeden wiersz na każde uruchomienie dla zlecenia.
-- Zlecenie może mieć kilka wpisów (automatyczny przydział przy utworzeniu, ponowne uruchomienie ręczne).

CREATE TABLE assignment_result (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT           NOT NULL REFERENCES transport_order (id),
    -- Wybrany kierowca; NULL, gdy nikt nie przeszedł filtrów (zlecenie zostaje NEW)
    driver_id  BIGINT REFERENCES driver (id),
    -- Ocena zwycięzcy 0..1; NULL razem z driver_id
    score      DOUBLE PRECISION CHECK (score >= 0 AND score <= 1),
    -- Pełne wyjaśnienie: wagi, ranking kandydatów (surowe i znormalizowane K1–K5, score),
    -- odrzuceni z powodem. Zapisujemy wagi, bo mogą się później zmienić w konfiguracji.
    details    JSONB            NOT NULL,
    created_at TIMESTAMP        NOT NULL DEFAULT now(),
    CHECK ((driver_id IS NULL) = (score IS NULL))
);

CREATE INDEX idx_assignment_result_order ON assignment_result (order_id);
