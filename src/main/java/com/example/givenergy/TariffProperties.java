package com.example.givenergy;

import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Electricity prices, in pence, used to value grid import and export. Import is on a two-rate time-of-use tariff:
 * the off-peak rate applies from {@code offPeakStart} to {@code offPeakEnd} (local time, may wrap midnight), the peak
 * rate the rest of the day. {@code standingChargePence} is the supplier's daily charge. Each stored day keeps the
 * prices in force when it was first written, so changing these settings values new days only.
 */
@Validated
@ConfigurationProperties("givenergy.tariff")
public record TariffProperties(
		@NotNull @PositiveOrZero @DefaultValue("16.5") Double exportPence,
		@NotNull @PositiveOrZero @DefaultValue("36.48") Double importPeakPence,
		@NotNull @PositiveOrZero @DefaultValue("6.6") Double importOffPeakPence,
		@NotNull @DefaultValue("23:30") LocalTime offPeakStart,
		@NotNull @DefaultValue("05:30") LocalTime offPeakEnd,
		@NotNull @PositiveOrZero @DefaultValue("0") Double standingChargePence) {

	/** Whether a half-hour starting at {@code time} is charged at the off-peak rate. */
	public boolean isOffPeak(LocalTime time) {
		return offPeakStart.isAfter(offPeakEnd)
				? !time.isBefore(offPeakStart) || time.isBefore(offPeakEnd)
				: !time.isBefore(offPeakStart) && time.isBefore(offPeakEnd);
	}

	/**
	 * The four hours after the off-peak window ends. A battery that is still low then didn't take the cheap charge;
	 * later in the day a low charge is just the house using it.
	 */
	public boolean isJustAfterOffPeak(LocalTime time) {
		LocalTime limit = offPeakEnd.plusHours(4);
		return limit.isAfter(offPeakEnd)
				? !time.isBefore(offPeakEnd) && time.isBefore(limit)
				: !time.isBefore(offPeakEnd) || time.isBefore(limit);
	}

}
