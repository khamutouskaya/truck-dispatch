-- Konta użytkowników aplikacji (logowanie, JWT).
-- Osobna tabela, a nie kolumny w driver: dyspozytor nie jest kierowcą, a dane logowania
-- (bezpieczeństwo) są oddzielone od danych kierowcy (dziedzina).
CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    -- Hasło przechowywane wyłącznie jako skrót BCrypt (nigdy jawnym tekstem)
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('DISPATCHER', 'DRIVER')),
    -- Konto kierowcy wskazuje swojego kierowcę (1:1); dyspozytor nie ma kierowcy
    driver_id     BIGINT UNIQUE REFERENCES driver (id),
    CHECK ((role = 'DRIVER') = (driver_id IS NOT NULL))
);

-- Konta demonstracyjne (dane testowe). Hasło wszystkich kont: haslo123 (skrót BCrypt, koszt 10).
INSERT INTO app_user (username, password_hash, role, driver_id)
VALUES ('dyspozytor',   '$2y$10$LwJv4Urh8VgqKH7vvA8fGORfVMbICuxS8WM70YoaEX7mSJGi1Ne9a', 'DISPATCHER', NULL),
       ('jan.kowalski', '$2y$10$mFou81MaijnyqzU0DZuA5O1YzO8Bx2u76C6geM48AFj1ZdX1eHkQq', 'DRIVER',
        (SELECT id FROM driver WHERE first_name = 'Jan' AND last_name = 'Kowalski')),
       ('piotr.nowak',  '$2y$10$tLj5WVhvHW5GkvhW/xHtg.jy9ndNIFqHxJQ9wDk6qBr8feQTGRPOK', 'DRIVER',
        (SELECT id FROM driver WHERE first_name = 'Piotr' AND last_name = 'Nowak')),
       ('grzegorz.szymanski', '$2y$10$oxk45avYMk7OLppj9qfb8uTM/2Gw53Ri9WlSWvqjK6izvjRTXTLI.', 'DRIVER',
        (SELECT id FROM driver WHERE first_name = 'Grzegorz' AND last_name = 'Szymański'));
