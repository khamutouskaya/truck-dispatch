-- Wysokość i masa własna pojazdu: potrzebne do sprawdzania ograniczeń krawędzi
-- (road_edge.max_height_m, road_edge.max_weight_t). Masa przy sprawdzaniu = masa własna + ładunek.
ALTER TABLE vehicle
    ADD COLUMN height_m        DOUBLE PRECISION NOT NULL DEFAULT 4.0   CHECK (height_m > 0),
    ADD COLUMN empty_weight_kg INTEGER          NOT NULL DEFAULT 14000 CHECK (empty_weight_kg > 0);

-- Wartości demonstracyjne (przybliżone, nie z dokumentacji konkretnych pojazdów):
-- ciągniki z naczepą ~4,0 m, mniejsze samochody ciężarowe poniżej 3,8 m —
-- przejadą pod umownym wiaduktem Piła–Bydgoszcz (max_height_m = 3.8 w V5).
UPDATE vehicle SET height_m = 4.0, empty_weight_kg = 14500 WHERE registration_number = 'WX 12345';
UPDATE vehicle SET height_m = 4.0, empty_weight_kg = 16000 WHERE registration_number = 'KR 23456';
UPDATE vehicle SET height_m = 3.7, empty_weight_kg = 15000 WHERE registration_number = 'EL 34567';
UPDATE vehicle SET height_m = 4.0, empty_weight_kg = 14000 WHERE registration_number = 'DW 45678';
UPDATE vehicle SET height_m = 4.0, empty_weight_kg = 14000 WHERE registration_number = 'PO 56789';
UPDATE vehicle SET height_m = 3.6, empty_weight_kg =  9000 WHERE registration_number = 'GD 67890';
UPDATE vehicle SET height_m = 3.5, empty_weight_kg =  8500 WHERE registration_number = 'SK 78901';
UPDATE vehicle SET height_m = 3.4, empty_weight_kg =  7500 WHERE registration_number = 'LU 89012';
UPDATE vehicle SET height_m = 3.7, empty_weight_kg = 11000 WHERE registration_number = 'ZS 90123';
UPDATE vehicle SET height_m = 3.7, empty_weight_kg = 10500 WHERE registration_number = 'RZ 01234';
UPDATE vehicle SET height_m = 3.6, empty_weight_kg = 11500 WHERE registration_number = 'CB 13579';
UPDATE vehicle SET height_m = 4.0, empty_weight_kg = 13000 WHERE registration_number = 'BI 24680';

-- DEFAULT był potrzebny tylko do wypełnienia istniejących wierszy;
-- nowe pojazdy muszą mieć te wartości podane jawnie (walidacja w VehicleRequest)
ALTER TABLE vehicle
    ALTER COLUMN height_m DROP DEFAULT,
    ALTER COLUMN empty_weight_kg DROP DEFAULT;
