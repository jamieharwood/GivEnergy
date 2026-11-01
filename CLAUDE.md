# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & run

Spring Boot 4.1 / Java 21 / Gradle 9 wrapper (Kotlin DSL, `build.gradle.kts`). Dependency versions come from the Spring Boot BOM via `platform(SpringBootPlugin.BOM_COORDINATES)`, so starters are declared without versions. The system `java` on this machine is a JRE only (no `javac`), so point `JAVA_HOME` at an IntelliJ-managed JDK:

```bash
export JAVA_HOME=~/.jdks/ms-21.0.12
./gradlew build                                        # compile + all tests + boot jar (build/libs)
./gradlew test --tests BatteryServiceTests             # single test class
./gradlew test --tests 'GivEnergyClientTests.readsLatestSystemData'   # single method
GIVENERGY_API_TOKEN=... ./gradlew bootRun              # run; then GET http://localhost:8081/api/battery
```

Tests need no token. `@SpringBootTest` classes must set `givenergy.poll-interval=0` and `givenergy.grid-history.sync-on-startup=false` (see `GivEnergyApplicationTests`), or the background jobs start calling the API and writing to Mongo.

The default `spring.mongodb.uri` is the real database, and there is no separate dev profile, so a local `bootRun` writes to live data unless `MONGODB_URI` is set. Stop the Docker container first when running locally: two copies don't create duplicates, but they double the API calls.

Docker: `docker compose up -d --build` builds the multi-stage `Dockerfile` (layered boot jar via `-Djarmode=tools extract --layers --launcher`, non-root JRE image) and runs it on port 8081 (`GIVENERGY_PORT` overrides the host port). The token comes from `GIVENERGY_API_TOKEN` in the git- and docker-ignored `.env`; `config/` isn't copied into the image. In the container, `localhost` in `MONGODB_URI` means the container itself.

## Architecture

Reads battery data from the **GivEnergy cloud REST API** (`https://api.givenergy.cloud/v1`, bearer-token auth), not from the inverter over the LAN.

- `GivEnergyProperties` — `givenergy.*` config (validated record). `api-token` is required and comes from `GIVENERGY_API_TOKEN` or the git-ignored `config/application.properties` (auto-loaded from the working directory); `inverter-serial` is optional (if blank, `BatteryService` discovers and caches the first inverter via `GET /communication-device`); `poll-interval` of `0` disables `BatteryPoller`.
- `client/` — `GivEnergyClient` wraps `RestClient`. Every API response is wrapped in `{"data": ...}`, unwrapped via `DataEnvelope<T>`. DTO records map only the fields used; unknown JSON fields are ignored.
- `battery/` — `BatteryService` combines two calls per reading: `system-data/latest` (live SoC % and battery power) and `meter-data/latest` (today's charged/discharged kWh), into a `BatteryStatus`. Exposed by `BatteryController` at `GET /api/battery`. `BatteryPoller` logs it periodically and saves it as a `BatteryReading` in MongoDB (`spring.mongodb.uri`, default `mongodb://storenator.bluelarma.com:27017/givenergy`, collection `batteryReadings`, no auth). The document `_id` is `<serial>_<reading time>`, so re-polling an unchanged cloud reading upserts rather than duplicates.
- `BatterySummaryJob` runs on cron `0 0 0 * * *` in `givenergy.timezone` (default `Europe/London`) and writes yesterday's per-inverter min/max SoC, peak charge/discharge watts and day's kWh to `batterySummaries` (`_id` `<serial>_<date>`, so re-runs replace). `POST /api/battery/summaries/{yyyy-MM-dd}` runs it on demand. Day boundaries are local midnight, so DST days are 23h/25h.
- Web UI: `src/main/resources/static/index.html` (served at `/`), with ARIA tabs Grid (default; export card above import) and Battery state of charge. The tab choice is kept in localStorage `tab`. Charts skip rendering while their panel is hidden (zero width) and redraw from a ResizeObserver when shown. It's a single self-contained file with hand-drawn SVG candlesticks and stacked import/export bars and no JS libraries or build step. It calls `GET /api/battery/candles?range=day|week|year` (`CandleService`): the day view is hourly candles from `batteryReadings`; week and year are daily candles from `batterySummaries` (needs `open`/`closeStateOfChargePercent`) plus today computed live from readings.
- `grid/` stores daily grid history in `gridExports` (solar→grid, battery→grid; energy-flows types 2 and 6) and `gridImports` (grid→home, grid→battery; types 3 and 4, plus `offPeakImportKwh`). Both collections use `_id` `<serial>_<date>` and implement `GridDay`. `GridHistorySync` fetches **half-hourly** energy-flows (grouping 0, max 1000 buckets per call, hence `CHUNK_DAYS = 18`), sums them into local days, and splits import by `TariffProperties.isOffPeak` on each bucket's local start time.
  - It back-fills from the commissioning date when either collection is empty, or when the latest import has no off-peak split (`null`, from before the split existed). Otherwise, at startup and at 00:30 local, it re-fetches the last 2 stored days plus anything newer.
  - It writes only new or changed days. `POST /api/grid/sync[?from=date]` runs it on demand.
  - `GET /api/grid/imports|exports?range=yesterday|week|month|year` returns a generic `GridView` (parts keyed by `GridFlow.parts()`, `kwhByRate`, `amountGbp`, import-only `amountGbpByRate` rounded per rate per bar so the parts add up to `amountGbp`, `tariff`; year = 12 monthly bars).
  - Money is computed at read time from `givenergy.tariff.*`, at current prices for all history, so changing a price needs no migration. The off-peak *window* is different: the split is stored, so changing it needs `POST /api/grid/sync?from=<first date>`. The UI draws import and export on a shared y-scale.
- energy-flows quirks (verified against data-points meter counters and integrated grid power): `end_time` is exclusive. The **daily** grouping (1) is badly wrong on some BST days (e.g. 15 Jul 2026: 13.9 vs 21.1 kWh), so don't use it. Half-hourly buckets are labelled in local time and match the meter, apart from small shifts between adjacent days when the inverter clock drifts. Requests are still padded by a day either side, keeping only the inner days.
- `MongoConfig` uses the driver's java.time codecs so `LocalDate` is stored as UTC midnight. Spring Data's default uses the JVM zone, and this machine's JVM picks up Europe/London from /etc/timezone, so summer dates came out as 23:00 the day before.
- Time comes from the injected `Clock` bean (UTC), converted with `givenergy.timezone` for local days, not from `now()` or the JVM zone.
- Range queries on `time` need an explicit `@Query` (`$gte`/`$lt`): derived `...GreaterThanEqualAndTimeLessThan` fails at runtime because both criteria target the same field. Mocked repository tests won't catch this.
- `ApiExceptionHandler` maps `GivEnergyAuthenticationException` (thrown by the client on 401/403) to a 502 `ProblemDetail`.

**Sign convention:** GivEnergy reports `battery.power` as positive = discharging, negative = charging. `BatteryService.toStatus` turns that into `BatteryState` plus separate non-negative charge and discharge watt values.

Cloud data updates roughly every 5 minutes and the API is rate-limited, so don't poll aggressively.

Spring Boot 4 notes: Mongo connection properties are `spring.mongodb.*` (not `spring.data.mongodb.*`); starters are modular (`spring-boot-starter-webmvc`, `spring-boot-starter-restclient`, and `*-test` counterparts), and Jackson is v3 (`tools.jackson.*` packages; annotations remain `com.fasterxml.jackson.annotation`).
