package com.example.givenergy.battery;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Open/high/low/close of the battery state of charge (percent) over {@code [start, end)}.
 * {@code chargedKwh}/{@code dischargedKwh} are only set for whole-day candles.
 */
public record Candle(
		Instant start,
		Instant end,
		int open,
		int high,
		int low,
		int close,
		int readingCount,
		Double chargedKwh,
		Double dischargedKwh) {

	static Candle of(Instant start, Instant end, List<BatteryReading> readings, boolean withEnergy) {
		List<BatteryReading> sorted = readings.stream().sorted(Comparator.comparing(BatteryReading::time)).toList();
		BatteryReading first = sorted.getFirst();
		BatteryReading last = sorted.getLast();
		return new Candle(start, end, first.stateOfChargePercent(),
				sorted.stream().mapToInt(BatteryReading::stateOfChargePercent).max().orElseThrow(),
				sorted.stream().mapToInt(BatteryReading::stateOfChargePercent).min().orElseThrow(),
				last.stateOfChargePercent(), sorted.size(),
				withEnergy ? last.chargedTodayKwh() : null, withEnergy ? last.dischargedTodayKwh() : null);
	}

	static Candle of(BatterySummary summary, Instant start, Instant end) {
		return new Candle(start, end, summary.openStateOfChargePercent(), summary.maxStateOfChargePercent(),
				summary.minStateOfChargePercent(), summary.closeStateOfChargePercent(), summary.readingCount(),
				summary.chargedKwh(), summary.dischargedKwh());
	}

}
