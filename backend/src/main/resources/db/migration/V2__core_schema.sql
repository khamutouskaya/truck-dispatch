-- Заглушка из V1 больше не нужна
DROP TABLE app_info;

-- Пункты на карте: города и развязки (узлы графа дорог)
CREATE TABLE road_node (
                           id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                           name                 VARCHAR(100)     NOT NULL UNIQUE,
                           lat                  DOUBLE PRECISION NOT NULL,
                           lon                  DOUBLE PRECISION NOT NULL,
                           default_handling_min INTEGER          NOT NULL DEFAULT 60
);

CREATE TABLE driver (
                        id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        first_name             VARCHAR(50) NOT NULL,
                        last_name              VARCHAR(50) NOT NULL,
                        phone                  VARCHAR(20),
                        status                 VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                            CHECK (status IN ('AVAILABLE', 'ON_ROUTE', 'RESTING', 'OFF_DUTY')),
                        current_lat            DOUBLE PRECISION,
                        current_lon            DOUBLE PRECISION,
                        location_updated_at    TIMESTAMP,
                        location_source        VARCHAR(20)
                            CHECK (location_source IN ('MANUAL', 'PHONE', 'SIMULATION')),
                        driving_time_today_min INTEGER NOT NULL DEFAULT 0,
                        driving_time_week_min  INTEGER NOT NULL DEFAULT 0,
                        continuous_driving_min INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE vehicle (
                         id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         registration_number VARCHAR(15)      NOT NULL UNIQUE,
                         type                VARCHAR(20)      NOT NULL
                             CHECK (type IN ('TILT', 'REFRIGERATOR', 'TANKER', 'FLATBED', 'CONTAINER')),
                         capacity_kg         INTEGER          NOT NULL CHECK (capacity_kg > 0),
                         volume_m3           DOUBLE PRECISION NOT NULL CHECK (volume_m3 > 0),
                         driver_id           BIGINT UNIQUE REFERENCES driver (id)
);

CREATE TABLE transport_order (
                                 id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 loading_node_id         BIGINT           NOT NULL REFERENCES road_node (id),
                                 unloading_node_id       BIGINT           NOT NULL REFERENCES road_node (id),
                                 cargo_weight_kg         INTEGER          NOT NULL CHECK (cargo_weight_kg > 0),
                                 cargo_volume_m3         DOUBLE PRECISION NOT NULL CHECK (cargo_volume_m3 > 0),
                                 required_vehicle_type   VARCHAR(20)      NOT NULL
                                     CHECK (required_vehicle_type IN ('TILT', 'REFRIGERATOR', 'TANKER', 'FLATBED', 'CONTAINER')),
                                 loading_deadline        TIMESTAMP        NOT NULL,
                                 delivery_deadline       TIMESTAMP        NOT NULL,
                                 estimated_loading_min   INTEGER,
                                 estimated_unloading_min INTEGER,
                                 status                  VARCHAR(20)      NOT NULL DEFAULT 'NEW'
                                     CHECK (status IN ('NEW', 'ASSIGNED', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
                                 assigned_driver_id      BIGINT REFERENCES driver (id),
                                 created_at              TIMESTAMP        NOT NULL DEFAULT now(),
                                 CHECK (delivery_deadline > loading_deadline)
);