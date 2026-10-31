package com.example.givenergy.battery;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A battery status stored in MongoDB. The id combines inverter serial and reading time, so polling faster than the
 * cloud updates (~5 min) overwrites the same document instead of storing duplicates.
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
		double dischargedTodayKwh) {

	static BatteryReading from(BatteryStatus status, Instant recordedAt) {
		Instant time = status.time().toInstant();
		return new BatteryReading(status.inverterSerial() + "_" + time, status.inverterSerial(), time, recordedAt,
				status.stateOfChargePercent(), status.state(), status.chargePowerWatts(),
				status.dischargePowerWatts(), status.temperatureCelsius(), status.chargedTodayKwh(),
				status.dischargedTodayKwh());
	}

}
