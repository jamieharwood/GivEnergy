package com.example.givenergy.battery;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/** One sample on the power and temperature chart. Watts are 0 when the reading predates those fields. */
public record TracePoint(
		Instant time,
		int stateOfChargePercent,
		Double temperatureCelsius,
		int chargePowerWatts,
		int dischargePowerWatts,
		int solarPowerWatts,
		int gridExportPowerWatts,
		int gridImportPowerWatts,
		int consumptionPowerWatts) {

	static TracePoint from(BatteryReading reading) {
		return new TracePoint(reading.time(), reading.stateOfChargePercent(), reading.temperatureCelsius(),
				reading.chargePowerWatts(), reading.dischargePowerWatts(), reading.solarWatts(),
				reading.gridExportWatts(), reading.gridImportWatts(), reading.consumptionWatts());
	}

	static TracePoint average(Instant hour, List<BatteryReading> readings) {
		List<BatteryReading> sorted = readings.stream()
				.sorted(Comparator.comparing(BatteryReading::time)).toList();
		BatteryReading last = sorted.getLast();
		double temperature = sorted.stream().filter(r -> r.temperatureCelsius() != null)
				.mapToDouble(BatteryReading::temperatureCelsius).average().orElse(Double.NaN);
		return new TracePoint(hour, last.stateOfChargePercent(),
				Double.isNaN(temperature) ? null : Math.round(temperature * 10) / 10.0,
				mean(sorted, BatteryReading::chargePowerWatts),
				mean(sorted, BatteryReading::dischargePowerWatts),
				mean(sorted, BatteryReading::solarWatts),
				mean(sorted, BatteryReading::gridExportWatts),
				mean(sorted, BatteryReading::gridImportWatts),
				mean(sorted, BatteryReading::consumptionWatts));
	}

	private static int mean(List<BatteryReading> readings, ToIntFunction<BatteryReading> value) {
		return (int) Math.round(readings.stream().mapToInt(value).average().orElse(0));
	}

}
