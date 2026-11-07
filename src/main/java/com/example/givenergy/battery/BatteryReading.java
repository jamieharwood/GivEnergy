package com.example.givenergy.battery;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A battery status stored in MongoDB. The id combines inverter serial and reading time, so polling faster than the
 * cloud updates (~5 min) overwrites the same document instead of storing duplicates. Power fields added after the
 * first release are nullable so documents written before then still load.
 */
@Document("batteryReadings")
public record BatteryReading(
		@Id String id,
		String inverterSerial,
		Instant time,
		Instant recordedAt,
		int stateOfChargePercent,
		BatteryState state,
		int chargePowerWatts,
		int dischargePowerWatts,
		Double temperatureCelsius,
		double chargedTodayKwh,
		double dischargedTodayKwh,
		String inverterStatus,
		Integer solarPowerWatts,
		Integer gridExportPowerWatts,
		Integer gridImportPowerWatts,
		Integer consumptionPowerWatts) {

	static BatteryReading from(BatteryStatus status, Instant recordedAt) {
		Instant time = status.time().toInstant();
		BatteryStatus.PowerNow power = status.power();
		return new BatteryReading(status.inverterSerial() + "_" + time, status.inverterSerial(), time, recordedAt,
				status.stateOfChargePercent(), status.state(), status.chargePowerWatts(),
				status.dischargePowerWatts(), status.temperatureCelsius(), status.chargedTodayKwh(),
				status.dischargedTodayKwh(), status.inverterStatus(),
				power == null ? null : power.solarWatts(),
				power == null ? null : power.gridExportWatts(),
				power == null ? null : power.gridImportWatts(),
				power == null ? null : power.consumptionWatts());
	}

	int solarWatts() {
		return solarPowerWatts == null ? 0 : solarPowerWatts;
	}

	int gridExportWatts() {
		return gridExportPowerWatts == null ? 0 : gridExportPowerWatts;
	}

	int gridImportWatts() {
		return gridImportPowerWatts == null ? 0 : gridImportPowerWatts;
	}

	int consumptionWatts() {
		return consumptionPowerWatts == null ? 0 : consumptionPowerWatts;
	}

}
