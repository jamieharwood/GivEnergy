# GivEnergy Battery Reader

Spring Boot 4 app that reads battery **state of charge** and **charge/discharge** power and daily energy from a GivEnergy inverter, using the GivEnergy cloud API.

## Setup

1. At https://www.givenergy.cloud go to **Account Settings → Manage API Tokens** and create a token with the `api:inverter:read` scope.
2. Open this folder in IntelliJ (it's detected as a Gradle project) and set the Project SDK to Java 21+.
3. Provide the token in one of two ways:
   - In `config/application.properties` (git-ignored), uncomment `givenergy.api-token=` and paste your token after it, **or**
   - Set the environment variable `GIVENERGY_API_TOKEN` in the IntelliJ run configuration. For a Gradle `bootRun` configuration, it goes in the **Environment variables** field of that Gradle configuration.

   Optionally also set `givenergy.inverter-serial` / `GIVENERGY_INVERTER_SERIAL`; if it's not set, the first inverter on the account is used.
4. Run it, then open http://localhost:8080/api/battery:

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
  "dischargedTodayKwh": 1.1
}
```

Every 5 minutes (`givenergy.poll-interval`; `0` turns it off) the app also logs the battery status and records it in MongoDB: database `givenergy`, collection `batteryReadings` on `storenator.bluelarma.com:27017`. To use a different server, set `MONGODB_URI`. Each cloud reading is stored once, even if it's polled more than once.

At 00:00 UK time (`givenergy.timezone`) a scheduled job summarises the previous day into the `batterySummaries` collection, one document per inverter per day. It records the minimum and maximum state of charge and when each happened, the peak charge and discharge power, and the kWh charged and discharged that day. To build or rebuild a day's summary yourself, run `curl -X POST localhost:8081/api/battery/summaries/2026-10-31`.
