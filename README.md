# truck-dispatch

System for automatic assignment of transport orders to truck drivers with an authorial multi-criteria algorithm
(engineering thesis).

## Running

Requirements: Java 25, Docker.

```bash
docker compose up -d          # PostgreSQL 16 (required by the application and integration tests)
cd backend
./mvnw spring-boot:run        # API on http://localhost:8080, Swagger UI: http://localhost:8080/swagger-ui.html
./mvnw test                   # all tests except the long experiment
./mvnw test -Pexperiment      # ETA learning experiment only (~30 s), CSV in experiments/results/
```

### Road graph generator

`tools/road-graph/build_graph.py` builds the Flyway migration `V5__road_graph.sql` from `nodes.csv` and `edges.csv`
using openrouteservice. The API key is read from the `ORS_API_KEY` environment variable and is never stored
in the repository. Responses are cached in `cache/`, so a re-run after editing the CSV files needs no API calls.

```bash
cd tools/road-graph
export ORS_API_KEY=...
python3 build_graph.py
```

Applied Flyway migrations must not be changed: regenerated data goes into a new migration.

## Demo accounts

Demo data only (Flyway migration `V9__app_user.sql`), **not secrets** — for local runs and the presentation.

| Username | Password | Role | Driver |
|---|---|---|---|
| `dyspozytor` | `haslo123` | DISPATCHER | — |
| `jan.kowalski` | `haslo123` | DRIVER | Jan Kowalski (id 1, Warszawa) |
| `piotr.nowak` | `haslo123` | DRIVER | Piotr Nowak (id 2, Kraków) |
| `grzegorz.szymanski` | `haslo123` | DRIVER | Grzegorz Szymański (id 9, on route) |

Log in: `POST /api/auth/login` with `{"username": "dyspozytor", "password": "haslo123"}`,
then send the returned token as `Authorization: Bearer <accessToken>`. The token is valid for 8 hours.

## JWT secret

Tokens are signed with `jwt.secret` (HMAC-SHA256, at least 32 bytes), read from the `JWT_SECRET` environment variable.
Without it the application uses a built-in **development-only** value from `application.yaml`.
Set your own `JWT_SECRET` for any shared or public deployment.
