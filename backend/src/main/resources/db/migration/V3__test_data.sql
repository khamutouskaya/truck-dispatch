-- Демонстрационные данные. id не указываем: колонки GENERATED ALWAYS AS IDENTITY,
-- связи ставим подзапросами по уникальным полям (name, registration_number, имя водителя).

-- 15 городов Польши
INSERT INTO road_node (name, lat, lon, default_handling_min)
VALUES ('Warszawa',    52.2297, 21.0122, 90),
       ('Kraków',      50.0647, 19.9450, 75),
       ('Łódź',        51.7592, 19.4560, 60),
       ('Wrocław',     51.1079, 17.0385, 75),
       ('Poznań',      52.4064, 16.9252, 60),
       ('Gdańsk',      54.3520, 18.6466, 90),
       ('Szczecin',    53.4285, 14.5528, 75),
       ('Katowice',    50.2649, 19.0238, 60),
       ('Lublin',      51.2465, 22.5684, 45),
       ('Białystok',   53.1325, 23.1688, 45),
       ('Rzeszów',     50.0412, 21.9991, 45),
       ('Bydgoszcz',   53.1235, 18.0084, 60),
       ('Toruń',       53.0138, 18.5984, 30),
       ('Częstochowa', 50.8118, 19.1203, 30),
       ('Kielce',      50.8661, 20.6286, 30);

-- 12 водителей. Лимиты ЕС 561/2006: 540 мин/день, 3360 мин/неделю, 270 мин без перерыва.
INSERT INTO driver (first_name, last_name, phone, status, current_lat, current_lon,
                    location_updated_at, location_source,
                    driving_time_today_min, driving_time_week_min, continuous_driving_min)
VALUES ('Jan',       'Kowalski',     '+48 501 234 567', 'AVAILABLE', 52.2297, 21.0122, now(), 'MANUAL',   0, 1200,   0),
       ('Piotr',     'Nowak',        '+48 502 345 678', 'AVAILABLE', 50.0647, 19.9450, now(), 'MANUAL', 120, 1850,  45),
       ('Krzysztof', 'Wiśniewski',   '+48 503 456 789', 'AVAILABLE', 51.7592, 19.4560, now(), 'MANUAL',  60,  900,  60),
       ('Tomasz',    'Wójcik',       '+48 504 567 890', 'AVAILABLE', 51.1079, 17.0385, now(), 'MANUAL', 240, 2400,   0),
       ('Andrzej',   'Kowalczyk',    '+48 505 678 901', 'AVAILABLE', 52.4064, 16.9252, now(), 'MANUAL',   0,  600,   0),
       ('Paweł',     'Kamiński',     '+48 506 789 012', 'AVAILABLE', 54.3520, 18.6466, now(), 'MANUAL', 300, 2900, 120),
       ('Marcin',    'Lewandowski',  '+48 507 890 123', 'AVAILABLE', 50.2649, 19.0238, now(), 'MANUAL',  90, 1500,  90),
       ('Michał',    'Zieliński',    '+48 508 901 234', 'AVAILABLE', 51.2465, 22.5684, now(), 'MANUAL', 180, 3100,  30),
       -- в пути: точка на трассе между городами
       ('Grzegorz',  'Szymański',    '+48 509 012 345', 'ON_ROUTE',  52.0100, 20.2500, now(), 'MANUAL', 200, 2100, 150),
       ('Łukasz',    'Woźniak',      '+48 510 123 456', 'ON_ROUTE',  50.4500, 20.3000, now(), 'MANUAL', 330, 2700, 210),
       -- отдыхает после почти полного дневного лимита
       ('Mariusz',   'Dąbrowski',    '+48 511 234 567', 'RESTING',   53.1235, 18.0084, now(), 'MANUAL', 520, 3000,   0),
       -- не на смене: положение неизвестно
       ('Rafał',     'Kozłowski',    '+48 512 345 678', 'OFF_DUTY',  NULL,    NULL,    NULL,  NULL,       0,    0,   0);

-- 12 машин всех 5 типов, каждая привязана к одному водителю
INSERT INTO vehicle (registration_number, type, capacity_kg, volume_m3, driver_id)
VALUES ('WX 12345', 'TILT',         24000, 90, (SELECT id FROM driver WHERE first_name = 'Jan'       AND last_name = 'Kowalski')),
       ('KR 23456', 'REFRIGERATOR', 18000, 70, (SELECT id FROM driver WHERE first_name = 'Piotr'     AND last_name = 'Nowak')),
       ('EL 34567', 'TANKER',       20000, 40, (SELECT id FROM driver WHERE first_name = 'Krzysztof' AND last_name = 'Wiśniewski')),
       ('DW 45678', 'FLATBED',      22000, 60, (SELECT id FROM driver WHERE first_name = 'Tomasz'    AND last_name = 'Wójcik')),
       ('PO 56789', 'CONTAINER',    24000, 80, (SELECT id FROM driver WHERE first_name = 'Andrzej'   AND last_name = 'Kowalczyk')),
       ('GD 67890', 'TILT',         12000, 60, (SELECT id FROM driver WHERE first_name = 'Paweł'     AND last_name = 'Kamiński')),
       ('SK 78901', 'REFRIGERATOR', 10000, 45, (SELECT id FROM driver WHERE first_name = 'Marcin'    AND last_name = 'Lewandowski')),
       ('LU 89012', 'TILT',          8000, 40, (SELECT id FROM driver WHERE first_name = 'Michał'    AND last_name = 'Zieliński')),
       ('ZS 90123', 'FLATBED',      16000, 55, (SELECT id FROM driver WHERE first_name = 'Grzegorz'  AND last_name = 'Szymański')),
       ('RZ 01234', 'REFRIGERATOR', 14000, 50, (SELECT id FROM driver WHERE first_name = 'Łukasz'    AND last_name = 'Woźniak')),
       ('CB 13579', 'TANKER',       15000, 42, (SELECT id FROM driver WHERE first_name = 'Mariusz'   AND last_name = 'Dąbrowski')),
       ('BI 24680', 'CONTAINER',    20000, 86, (SELECT id FROM driver WHERE first_name = 'Rafał'     AND last_name = 'Kozłowski'));

-- 6 новых заказов. Для каждого есть хотя бы один свободный водитель с подходящей машиной.
-- Сроки считаются от момента применения миграции.
INSERT INTO transport_order (loading_node_id, unloading_node_id, cargo_weight_kg, cargo_volume_m3,
                             required_vehicle_type, loading_deadline, delivery_deadline,
                             estimated_loading_min, estimated_unloading_min, status)
VALUES ((SELECT id FROM road_node WHERE name = 'Warszawa'), (SELECT id FROM road_node WHERE name = 'Gdańsk'),
        18000, 70.0, 'TILT',         now() + interval '1 day',           now() + interval '2 days',          NULL, NULL, 'NEW'),
       ((SELECT id FROM road_node WHERE name = 'Kraków'),   (SELECT id FROM road_node WHERE name = 'Szczecin'),
        12000, 50.0, 'REFRIGERATOR', now() + interval '1 day',           now() + interval '3 days',          45,   60,   'NEW'),
       ((SELECT id FROM road_node WHERE name = 'Łódź'),     (SELECT id FROM road_node WHERE name = 'Rzeszów'),
        16000, 35.0, 'TANKER',       now() + interval '2 days',          now() + interval '3 days',          90,   90,   'NEW'),
       ((SELECT id FROM road_node WHERE name = 'Wrocław'),  (SELECT id FROM road_node WHERE name = 'Białystok'),
        20000, 50.0, 'FLATBED',      now() + interval '1 day 6 hours',   now() + interval '3 days',          NULL, NULL, 'NEW'),
       ((SELECT id FROM road_node WHERE name = 'Poznań'),   (SELECT id FROM road_node WHERE name = 'Katowice'),
        22000, 75.0, 'CONTAINER',    now() + interval '12 hours',        now() + interval '1 day 12 hours',  60,   NULL, 'NEW'),
       ((SELECT id FROM road_node WHERE name = 'Lublin'),   (SELECT id FROM road_node WHERE name = 'Toruń'),
         7000, 35.0, 'TILT',         now() + interval '1 day',           now() + interval '2 days',          NULL, 30,   'NEW');
