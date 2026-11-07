package com.example.givenergy.battery;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Open/high/low/close of the battery state of charge (percent) over {@code [start, end)}.
 * {@code chargedKwh}/{@code dischargedKwh} are only set for whole-day candles. {@code lowAt} and {@code highAt} are
 * when the low and high were first reached.
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
		Double dischargedKwh,
		Instant lowAt,
		Instant highAt,
		int maxChargePowerWatts,
		int maxDischargePowerWatts) {

	static Candle of(Instant start, Instant end, List<BatteryReading> readings, boolean withEnergy) {
		List<BatteryReading> sorted = readings.stream().sorted(Comparator.comparing(BatteryReading::time)).toList();
		BatteryReading first = sorted.getFirst();
		BatteryReading last = sorted.getLast();
		Comparator<BatteryReading> byTime = Comparator.comparing(BatteryReading::time);
		BatteryReading low = sorted.stream()
				.min(Comparator.comparingInt(BatteryReading::stateOfChargePercent).thenComparing(byTime))
				.orElseThrow();
		BatteryReading high = sorted.stream()
				.max(Comparator.comparingInt(BatteryReading::stateOfChargePercent).thenComparing(byTime.reversed()))
				.orElseThrow();
		return new Candle(start, end, first.stateOfChargePercent(), high.stateOfChargePercent(),
				low.stateOfChargePercent(), last.stateOfChargePercent(), sorted.size(),
				withEnergy ? last.chargedTodayKwh() : null, withEnergy ? last.dischargedTodayKwh() : null,
				low.time(), high.time(),
				sorted.stream().mapToInt(BatteryReading::chargePowerWatts).max().orElse(0),
				sorted.stream().mapToInt(BatteryReading::dischargePowerWatts).max().orElse(0));
	}

	static Candle of(BatterySummary summary, Instant start, Instant end) {
		return new Candle(start, end, summary.openStateOfChargePercent(), summary.maxStateOfChargePercent(),
				summary.minStateOfChargePercent(), summary.closeStateOfChargePercent(), summary.readingCount(),
				summary.chargedKwh(), summary.dischargedKwh(), summary.minStateOfChargeAt(),
				summary.maxStateOfChargeAt(), summary.maxChargePowerWatts(), summary.maxDischargePowerWatts());
	}

}
