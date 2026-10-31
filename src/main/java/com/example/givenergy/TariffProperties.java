package com.example.givenergy;

import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Electricity prices, in pence per kWh, used to value grid import and export. Import is on a two-rate time-of-use
 * tariff: the off-peak rate applies from {@code offPeakStart} to {@code offPeakEnd} (local time, may wrap midnight),
 * the peak rate the rest of the day. History is valued at these current prices.
 */
@Validated
@ConfigurationProperties("givenergy.tariff")
public record TariffProperties(
		@NotNull @PositiveOrZero @DefaultValue("16.5") Double exportPence,
		@NotNull @PositiveOrZero @DefaultValue("36.48") Double importPeakPence,
		@NotNull @PositiveOrZero @DefaultValue("6.6") Double importOffPeakPence,
		@NotNull @DefaultValue("23:30") LocalTime offPeakStart,
		@NotNull @DefaultValue("05:30") LocalTime offPeakEnd) {

	/** Whether a half-hour starting at {@code time} is charged at the off-peak rate. */
	public boolean isOffPeak(LocalTime time) {
		return offPeakStart.isAfter(offPeakEnd)
				? !time.isBefore(offPeakStart) || time.isBefore(offPeakEnd)
				: !time.isBefore(offPeakStart) && time.isBefore(offPeakEnd);
	}

}
