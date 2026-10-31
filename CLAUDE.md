# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & run

Spring Boot 4.1 / Java 21 / Gradle 9 wrapper (Kotlin DSL, `build.gradle.kts`). Dependency versions come from the Spring Boot BOM via `platform(SpringBootPlugin.BOM_COORDINATES)`, so starters are declared without versions. The system `java` on this machine is a JRE only (no `javac`), so point `JAVA_HOME` at an IntelliJ-managed JDK:

```bash
export JAVA_HOME=~/.jdks/ms-21.0.12
./gradlew build                                        # compile + all tests + boot jar (build/libs)
./gradlew test --tests BatteryServiceTests             # single test class
./gradlew test --tests 'GivEnergyClientTests.readsLatestSystemData'   # single method
GIVENERGY_API_TOKEN=... ./gradlew bootRun              # run; then GET http://localhost:8080/api/battery
```

## Architecture

Reads battery data from the **GivEnergy cloud REST API** (`https://api.givenergy.cloud/v1`, bearer-token auth), not from the inverter over the LAN.

- `GivEnergyProperties` — `givenergy.*` config (validated record). `api-token` is required and comes from `GIVENERGY_API_TOKEN` or the git-ignored `config/application.properties` (auto-loaded from the working directory); `inverter-serial` is optional (if blank, `BatteryService` discovers and caches the first inverter via `GET /communication-device`); `poll-interval` of `0` disables `BatteryPoller`.
- `client/` — `GivEnergyClient` wraps `RestClient`. Every API response is wrapped in `{"data": ...}`, unwrapped via `DataEnvelope<T>`. DTO records map only the fields used; unknown JSON fields are ignored.
- `battery/` — `BatteryService` combines two calls per reading: `system-data/latest` (live SoC % and battery power) and `meter-data/latest` (today's charged/discharged kWh), into a `BatteryStatus`. Exposed by `BatteryController` at `GET /api/battery`. `BatteryPoller` logs it periodically and saves it as a `BatteryReading` in MongoDB (`spring.mongodb.uri`, default `mongodb://storenator.bluelarma.com:27017/givenergy`, collection `batteryReadings`, no auth). The document `_id` is `<serial>_<reading time>`, so re-polling an unchanged cloud reading upserts rather than duplicates.
- `BatterySummaryJob` runs on cron `0 0 0 * * *` in `givenergy.timezone` (default `Europe/London`) and writes yesterday's per-inverter min/max SoC, peak charge/discharge watts and day's kWh to `batterySummaries` (`_id` `<serial>_<date>`, so re-runs replace). `POST /api/battery/summaries/{yyyy-MM-dd}` runs it on demand. Day boundaries are local midnight, so DST days are 23h/25h.
- Range queries on `time` need an explicit `@Query` (`$gte`/`$lt`): derived `...GreaterThanEqualAndTimeLessThan` fails at runtime because both criteria target the same field. Mocked repository tests won't catch this.
- `ApiExceptionHandler` maps `GivEnergyAuthenticationException` (thrown by the client on 401/403) to a 502 `ProblemDetail`.

**Sign convention:** GivEnergy reports `battery.power` as positive = discharging, negative = charging. `BatteryService.toStatus` turns that into `BatteryState` plus separate non-negative charge and discharge watt values.

Cloud data updates roughly every 5 minutes and the API is rate-limited, so don't poll aggressively.

Spring Boot 4 notes: Mongo connection properties are `spring.mongodb.*` (not `spring.data.mongodb.*`); starters are modular (`spring-boot-starter-webmvc`, `spring-boot-starter-restclient`, and `*-test` counterparts), and Jackson is v3 (`tools.jackson.*` packages; annotations remain `com.fasterxml.jackson.annotation`).
