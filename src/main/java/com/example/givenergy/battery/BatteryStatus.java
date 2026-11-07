package com.example.givenergy.battery;

import java.time.OffsetDateTime;

/**
 * Snapshot of one inverter: the battery, the live power flows around it, and the meter counters.
 *
 * @param stateOfChargePercent  0–100
 * @param state                 derived from the sign of the battery power
 * @param chargePowerWatts      current charge rate (0 when not charging)
 * @param dischargePowerWatts   current discharge rate (0 when not discharging)
 * @param chargedTodayKwh       energy into the battery since local midnight
 * @param dischargedTodayKwh    energy out of the battery since local midnight
 * @param inverterStatus        cloud status string, normally {@code Normal}
 * @param power                 instantaneous solar, grid and house watts
 * @param today                 meter counters since local midnight
 * @param lifetime              meter counters since commissioning
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
		double dischargedTodayKwh,
		String inverterStatus,
		PowerNow power,
		EnergyTotals today,
		EnergyTotals lifetime) {

	/**
	 * Instantaneous watts. Grid import and export are separate and never both non-zero: the cloud reports one
	 * signed grid power.
	 */
	public record PowerNow(int solarWatts, int gridExportWatts, int gridImportWatts, int consumptionWatts) {
	}

	/** Energy counters, in kWh. Battery charge is energy into the battery. */
	public record EnergyTotals(
			double solarKwh,
			double gridImportKwh,
			double gridExportKwh,
			double consumptionKwh,
			double batteryChargeKwh,
			double batteryDischargeKwh) {
	}

}
