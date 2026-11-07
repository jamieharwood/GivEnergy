# GivEnergy Battery Reader

A Spring Boot 4 app that reads a GivEnergy inverter and battery from the GivEnergy cloud API. It records the battery and the live power flows in MongoDB, keeps a daily history of solar, the house and grid import and export with what it cost and earned, and shows them on a web page with candlestick and bar charts.

![Dashboard in dark mode on the Grid tab: export and import by month for the last year, with costs and earnings](docs/screenshots/dashboard-dark.png)

## Features

- **Live status:** state of charge, battery, solar, house and grid power, inverter status and battery temperature, plus today's and lifetime energy counters, served as JSON.
- **Battery history:** a reading every 5 minutes, including those power readings, stored in MongoDB and summarised each midnight into the day's open, close, minimum and maximum charge and peak charge and discharge power.
- **Energy history:** daily kWh, back-filled automatically from the day the inverter was installed and kept up to date each night:
  - import, split into grid → home and grid → battery, and into off-peak and peak
  - export, split into solar → grid and battery → grid
  - solar generation (home, battery and grid) and what supplied the house (solar, battery and grid)
  - the last 7 days kept as half-hour buckets, including today
- **Costs and earnings:** import is priced on a two-rate time-of-use tariff and export at a flat rate. The default prices are 36.48p peak, 6.6p off-peak (23:30–05:30) and 16.5p for export. A standing charge, in pence per day, defaults to 0. Each day keeps the prices from the first time it was stored, so a later price change does not revalue history. The net for a period is import cost plus standing charge minus export earnings.
- **Web dashboard** with three tabs, **Grid** (shown first), **Solar** and **Battery**:
  - Grid: export and import charts on a shared scale, a net bill for the period, and the last two days half-hourly against the off-peak window
  - Solar: where the solar went, and what supplied the house, with self-consumption and self-sufficiency
  - Battery: a candlestick chart of state of charge (Day / Week / Month / Year) and a solar, house, battery and temperature trace for the day and week
  - a date to end the period on, and a comparison with the same window a year earlier
  - CSV download, hover and keyboard tooltips, a table view of each chart, light and dark mode, and a phone layout
  - an inverter picker when the account has more than one, and a banner when the battery is still low just after off-peak, the inverter is not "Normal", or a poll failed
- **No duplicate data:** every stored record has an ID made of the inverter serial and the date or time, so re-fetching overwrites rather than adds.

## Screenshots

| Grid tab (Month view) | Battery state of charge tab (Day view) |
|---|---|
| ![Daily grid export and import bars for the last 30 days, with a tooltip](docs/screenshots/grid-month.png) | ![Hourly candlesticks of battery charge over the last 24 hours, with a tooltip](docs/screenshots/battery-day.png) |

<img src="docs/screenshots/mobile.png" alt="Dashboard on a phone in dark mode" width="260" align="right">

The page also works on a phone (right).

These screenshots come from a real installation, with the inverter serial replaced by a placeholder. The battery charts use sample data, because that installation had only just started recording readings; the grid charts show its real history.

<br clear="right">

## How it works

```mermaid
flowchart LR
    GE[GivEnergy cloud API]
    GE -- every 5 min --> P[BatteryPoller]
    P --> R[(batteryReadings)]
    R -- 00:00 daily --> SJ[BatterySummaryJob]
    SJ --> S[(batterySummaries)]
    GE -- startup and 00:30 daily --> GS[GridHistorySync]
    GS --> X[(gridExports)]
    GS --> I[(gridImports)]
    GS --> B[(energyBalances)]
    GS --> H[(halfHourFlows)]
    R & S & X & I & B & H --> API[REST API]
    API --> UI[Web dashboard]
```

The app talks to the **GivEnergy cloud API** (`https://api.givenergy.cloud/v1`) with a personal API token. It doesn't connect to the inverter over your home network. The cloud updates about every 5 minutes, so that's how often the app polls.

Grid history is fetched as half-hourly buckets and added up into local days. The time of day is needed to split import into off-peak and peak. Half-hourly is also more accurate: GivEnergy's own daily totals are badly wrong on some summer days. All figures come from the inverter, so they can differ slightly from your supplier's bill. If the inverter's clock is off, a little energy can land on the neighbouring day.

| Data | GivEnergy endpoint |
|---|---|
| Live state of charge, solar, house, grid and battery power, status and temperature | `GET /inverter/{serial}/system-data/latest` |
| Today and lifetime solar, grid, house and battery kWh | `GET /inverter/{serial}/meter-data/latest` |
| Half-hourly energy flows (all seven: solar, battery and grid) | `POST /inverter/{serial}/energy-flows` |
| Inverter serial and installation date | `GET /communication-device` |

## Requirements

- **Java 21 or newer:** a full JDK, not just a runtime.
- **MongoDB:** the app has been run against MongoDB 8 with no authentication.
- **A GivEnergy account and API token:** the token needs at least the `api:inverter:read` permission.
- **Internet access:** to reach `api.givenergy.cloud`.

Gradle comes with the project through the wrapper (`./gradlew`), so you don't need to install it.

## Getting started

### 1. Create an API token

At [givenergy.cloud](https://www.givenergy.cloud), go to **Account Settings → Manage API Tokens** and create a token with the `api:inverter:read` permission.

### 2. Configure the app

Create `config/application.properties` in the project root. The `config/` folder is git-ignored, and Spring Boot loads it automatically when the app runs from the project folder:

```properties
givenergy.api-token=<your token>
# Optional: only needed if your account has more than one inverter
#givenergy.inverter-serial=CE1234G567
# Optional: if MongoDB isn't on the default host
#spring.mongodb.uri=mongodb://localhost:27017/givenergy
```

Alternatively, set the `GIVENERGY_API_TOKEN` and `MONGODB_URI` environment variables.

### 3. Run it

**From IntelliJ:** open the project folder (it's detected as a Gradle project), set the Project SDK to a Java 21+ JDK, and run `GivEnergyApplication` or the Gradle `bootRun` task.

**From a terminal:**

```bash
./gradlew bootRun
```

### 4. Open the dashboard

Go to <http://localhost:8081/>.

The first time the app starts, and the first start after upgrading to the solar and house history, it back-fills every energy flow from the installation date. That takes about 10 seconds for 4 years of data, and it stores the current prices on each day. Run that sync before changing a price if you want the old days to keep the old rate. Battery charts fill up as readings arrive, and daily battery candles appear after the first midnight summary. Today's daily bar is not drawn until the next night; today's kWh is in the header, and the half-hour chart includes today.

### Running in Docker

The project includes a `Dockerfile` (a multi-stage build: compile with the Gradle wrapper, then run on a Java 21 runtime image as a non-root user) and a `compose.yaml`.

Put the token in a `.env` file next to `compose.yaml`. It's git-ignored and kept out of the image:

```properties
GIVENERGY_API_TOKEN=<your token>
# Optional
#GIVENERGY_INVERTER_SERIAL=CE1234G567
#MONGODB_URI=mongodb://localhost:27017/givenergy
#GIVENERGY_PORT=8081
```

Then build and start it:

```bash
docker compose up -d --build     # build the image and start the container
docker compose logs -f           # follow the logs
docker compose down              # stop it
```

The dashboard is on <http://localhost:8081/>, or the port set by `GIVENERGY_PORT`. The container restarts automatically unless you stop it. `config/application.properties` isn't used inside the container, so set everything through `.env` or `environment:` in `compose.yaml`. Any property can be set there as an environment variable, for example `GIVENERGY_POLL_INTERVAL=10m`. The MongoDB host must be reachable from the container: `localhost` there means the container itself, so use the host's name or address.

Run only one copy of the app at a time (for example, stop it in IntelliJ first). Two copies don't create duplicate records, but they double the calls to the GivEnergy API.

## Configuration

These are set in `application.properties`, `config/application.properties` or on the command line (`--name=value`):

| Property | Environment variable | Default | Description |
|---|---|---|---|
| `givenergy.api-token` | `GIVENERGY_API_TOKEN` | *(required)* | GivEnergy API token |
| `givenergy.inverter-serial` | `GIVENERGY_INVERTER_SERIAL` | every inverter on the account | Limit the app to one inverter. Leave it blank to poll and sync all of them; the page then offers a picker |
| `givenergy.poll-interval` | | `5m` | How often to read and record the battery; `0` turns it off |
| `givenergy.timezone` | | `Europe/London` | Defines "a day" for summaries, and the time zone for the midnight jobs |
| `givenergy.grid-history.sync-on-startup` | | `true` | Back-fill or catch up grid import and export when the app starts |
| `givenergy.grid-history.cron` | | `0 30 0 * * *` | When to fetch the previous day's grid import and export |
| `givenergy.tariff.export-pence` | | `16.5` | Export payment, pence per kWh |
| `givenergy.tariff.import-peak-pence` | | `36.48` | Import price outside the off-peak window, pence per kWh |
| `givenergy.tariff.import-off-peak-pence` | | `6.6` | Import price inside the off-peak window, pence per kWh |
| `givenergy.tariff.off-peak-start` / `off-peak-end` | | `23:30` / `05:30` | Off-peak window in local time; it can wrap past midnight |
| `givenergy.tariff.standing-charge-pence` | | `0` | Supplier standing charge, pence per day, added to the net. `0` leaves existing totals unchanged |
| `givenergy.alerts.low-soc-percent` | | `20` | Warn when the battery is still below this percent in the four hours after off-peak ends. `0` turns that warning off |
| `spring.mongodb.uri` | `MONGODB_URI` | `mongodb://storenator.bluelarma.com:27017/givenergy` | MongoDB connection, including the database name |
| `server.port` | | `8081` | Web server port |

## REST API

| Method and path | Description |
|---|---|
| `GET /api/inverters` | Inverters on the account (`serial`, `model`) |
| `GET /api/alerts` | Current in-memory alerts (low charge just after off-peak, inverter status, a failed poll). Restarting the app clears them |
| `GET /api/battery` | Live status from GivEnergy, including power now and today/lifetime energy |
| `GET /api/battery/candles?range=day\|week\|month\|year` | State-of-charge candles. With no date, day is the last 24 hours, week 7 days, month 30 days and year 365 days |
| `GET /api/battery/traces?range=day\|week` | Solar, house, battery and temperature trace. Day is each reading; week is an hourly average |
| `GET /api/battery/candles.csv` | The candle chart as CSV |
| `POST /api/battery/summaries/{yyyy-MM-dd}` | Build or rebuild a day's battery summary |
| `GET /api/grid/imports` and `GET /api/grid/exports` | Grid totals and bars. Ranges are `yesterday`, `week`, `month` and `year` (12 monthly bars) |
| `GET /api/grid/imports.csv` and `GET /api/grid/exports.csv` | Those views as CSV |
| `GET /api/grid/bill` | Net for the same window: import cost + standing charge − export earnings |
| `GET /api/grid/half-hours?days=1..7` | Half-hour import and export for the recent days, including today |
| `GET /api/energy/solar` and `GET /api/energy/house` | Solar generation and house supply, with self-consumption or self-sufficiency |
| `GET /api/energy/solar.csv` and `GET /api/energy/house.csv` | Those views as CSV |
| `POST /api/grid/sync[?from=yyyy-MM-dd]` | Fetch energy flows now; with `from`, re-fetch every day since that date. Returns how many daily documents were written |
| `GET /actuator/health` | Health check, including MongoDB |

Chart requests accept `anchor=yyyy-MM-dd` (end the window on that date; a month is then that calendar month) and `compare=true` (the same window a year earlier). Add `inverter=<serial>` to read one inverter when the account has several. Omit it and the app uses the configured serial, or the first inverter.

Example: `GET /api/battery`

```json
{
  "inverterSerial": "CE1234G567",
  "time": "2026-10-31T12:00:00Z",
  "stateOfChargePercent": 76,
  "state": "CHARGING",
  "chargePowerWatts": 850,
  "dischargePowerWatts": 0,
  "temperatureCelsius": 21.5,
  "chargedTodayKwh": 3.4,
  "dischargedTodayKwh": 1.1,
  "inverterStatus": "Normal",
  "power": { "solarWatts": 1200, "gridExportWatts": 0, "gridImportWatts": 50, "consumptionWatts": 400 },
  "today": { "solarKwh": 5.1, "gridImportKwh": 1.2, "gridExportKwh": 0.4, "consumptionKwh": 4.0, "batteryChargeKwh": 3.4, "batteryDischargeKwh": 1.1 },
  "lifetime": { "solarKwh": 4200, "gridImportKwh": 3100, "gridExportKwh": 1800, "consumptionKwh": 5000, "batteryChargeKwh": 2600, "batteryDischargeKwh": 2400 }
}
```

`state` is `CHARGING`, `DISCHARGING` or `IDLE`. Grid power from the cloud is positive when the house is exporting; the status splits that into `gridExportWatts` and `gridImportWatts`. Battery power is positive when discharging.

The grid and energy endpoints include `amountGbp` (cost for import, earnings for export; solar and house are £0) for the whole range and for each bar. Import also includes `kwhByRate` and `amountGbpByRate` (`offPeak`, `peak`); `amountGbp` is the sum of the two rounded rate costs. Solar includes `selfConsumptionRatio` and the house view includes `selfSufficiencyRatio`. `mixedTariff` is true when the days in the window were priced at more than one tariff. If GivEnergy rejects the token, the endpoints return `502` with an explanation instead of a generic error.

## Data stored in MongoDB

All collections are in the database named in `spring.mongodb.uri`. Dates are stored as UTC midnight, and times as UTC.

| Collection | One document per | `_id` | Written by |
|---|---|---|---|
| `batteryReadings` | cloud reading (about every 5 minutes) | `<serial>_<reading time>` | `BatteryPoller` |
| `batterySummaries` | inverter per day: open/close/min/max charge, peak power, kWh in and out | `<serial>_<date>` | `BatterySummaryJob` at 00:00 |
| `gridExports` | inverter per day: solar → grid, battery → grid, total kWh, and the prices in force when the day was first stored | `<serial>_<date>` | `GridHistorySync` at startup and 00:30 |
| `gridImports` | inverter per day: grid → home, grid → battery, total and off-peak kWh, and the same stored prices | `<serial>_<date>` | `GridHistorySync` at startup and 00:30 |
| `energyBalances` | inverter per day: all seven flows (solar → home/battery/grid, battery → home/grid, grid → home/battery) | `<serial>_<date>` | `GridHistorySync` at startup and 00:30 |
| `halfHourFlows` | inverter per half hour, for the last 7 local days including today | `<serial>_<start>` | `GridHistorySync`; older buckets are deleted on each sync |

The pounds on a chart are calculated when the page asks for them, using the prices stored on that day. Days written before prices were stored pick up the current tariff on the next sync and then keep it. There is no separate restamp. Changing the off-peak *window* does change the stored split; after changing it, run `POST /api/grid/sync?from=<first date>`. Changing only the prices does not.

## Scheduled jobs

| Job | When | What it does |
|---|---|---|
| `BatteryPoller` | every `givenergy.poll-interval` | Reads every inverter, logs it, saves a `batteryReadings` document and updates the in-memory alerts |
| `BatterySummaryJob` | 00:00 in `givenergy.timezone` | Summarises the previous day's readings into `batterySummaries` |
| `GridHistorySync` | 15 s after startup, and at 00:30 | Fetches all seven energy flows for every day since the last stored one (or since installation, if export, the off-peak split or the energy balances are missing). It re-checks the last 2 stored days, writes days that are new or have changed, refreshes the 7-day half-hour cache, and stamps any day that has no stored prices |

## Development

```bash
./gradlew build                                   # compile, run all tests, build the jar in build/libs
./gradlew test --tests GridHistorySyncTests       # one test class
./gradlew test --tests 'CandleServiceTests.weekViewIsPastSummariesPlusToday'   # one test
java -jar build/libs/givenergy-0.0.1-SNAPSHOT.jar # run the built jar
```

### Project layout

```
src/main/java/com/example/givenergy/
├── client/     GivEnergy API client and response types
├── battery/    live status, readings, daily summaries, candlestick data
├── grid/       energy-flow sync, solar and house views, bills and half-hour data
└── *.java      application, configuration properties, Mongo config, error handling
src/main/resources/
├── application.properties
└── static/     the web dashboard (index.html, a single file with no build step) and favicons
```

The dashboard is plain HTML, CSS and JavaScript with hand-drawn SVG charts. It doesn't use any external libraries or CDNs.

## Troubleshooting

| Problem | Fix |
|---|---|
| `APPLICATION FAILED TO START … givenergy.apiToken … is required` | No token configured. Add it to `config/application.properties` or set `GIVENERGY_API_TOKEN`. A variable exported in a terminal doesn't reach apps started from IntelliJ; set it in the run configuration instead. |
| `GivEnergy rejected the API token (HTTP 401)` | The token is wrong, revoked or expired. Test it with `curl -H "Authorization: Bearer <token>" https://api.givenergy.cloud/v1/communication-device`. |
| `HTTP 403` / "lacks permission" | Create a token with the `api:inverter:read` permission. |
| `Port 8081 was already in use` | Stop the other process, or set `server.port`. |
| `release version 21 not supported` / no `javac` | The build is using a Java runtime rather than a JDK. Point `JAVA_HOME` (or IntelliJ's Project SDK) at a full JDK 21+. |
| Battery Week/Year charts only show today | Daily candles come from midnight summaries, so they appear one day at a time. To build a missed day, run `POST /api/battery/summaries/{date}`. |

## License

No license has been chosen yet.
