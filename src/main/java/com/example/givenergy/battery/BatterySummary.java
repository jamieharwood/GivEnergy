package com.example.givenergy.battery;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One inverter's battery activity over one local day. The id is {@code <serial>_<date>}, so re-running the summary
 * for a day replaces it.
 *
 * @param openStateOfChargePercent  state of charge at the day's first reading
 * @param closeStateOfChargePercent state of charge at the day's last reading
 * @param chargedKwh    energy into the battery that day (the day's last "charged today" counter)
 * @param dischargedKwh energy out of the battery that day (the day's last "discharged today" counter)
 */
@Document("batterySummaries")
public record BatterySummary(
		@Id String id,
		String inverterSerial,
		LocalDate date,
		String timezone,
		int readingCount,
		int openStateOfChargePercent,
		int closeStateOfChargePercent,
		int minStateOfChargePercent,
		Instant minStateOfChargeAt,
		int maxStateOfChargePercent,
		Instant maxStateOfChargeAt,
		int maxChargePowerWatts,
		int maxDischargePowerWatts,
		double chargedKwh,
		double dischargedKwh,
		Instant createdAt) {
}
