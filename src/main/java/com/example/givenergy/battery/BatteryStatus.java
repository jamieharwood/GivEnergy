package com.example.givenergy.battery;

import java.time.OffsetDateTime;

/**
 * Snapshot of the battery.
 *
 * @param stateOfChargePercent  0–100
 * @param state                 derived from the sign of the battery power
 * @param chargePowerWatts      current charge rate (0 when not charging)
 * @param dischargePowerWatts   current discharge rate (0 when not discharging)
 * @param chargedTodayKwh       energy into the battery since midnight
 * @param dischargedTodayKwh    energy out of the battery since midnight
 */
public record BatteryStatus(
		String inverterSerial,
		OffsetDateTime time,
		int stateOfChargePercent,
		BatteryState state,
		int chargePowerWatts,
		int dischargePowerWatts,
		Double temperatureCelsius,
		double chargedTodayKwh,
		double dischargedTodayKwh) {
}
