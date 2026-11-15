# truck-dispatch

System for automatic assignment of transport orders to truck drivers with an authorial multi-criteria algorithm
(engineering thesis).

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
