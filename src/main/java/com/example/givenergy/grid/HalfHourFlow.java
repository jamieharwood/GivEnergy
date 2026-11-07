package com.example.givenergy.grid;

import java.time.Instant;
import java.time.LocalDateTime;

import com.example.givenergy.client.EnergyFlowPeriod;
import com.example.givenergy.client.EnergyFlowType;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One half-hour of energy flows, kept for the recent days so the page can show when import happened against the
 * off-peak window. Older buckets are deleted. The id is {@code <serial>_<local start>}.
 */
@Document("halfHourFlows")
public record HalfHourFlow(
		@Id String id,
		String inverterSerial,
		LocalDateTime start,
		double solarToHomeKwh,
		double solarToBatteryKwh,
		double solarToGridKwh,
		double batteryToHomeKwh,
		double batteryToGridKwh,
		double gridToHomeKwh,
		double gridToBatteryKwh,
		boolean offPeak,
		Instant updatedAt) {

	static HalfHourFlow of(String serial, EnergyFlowPeriod period, boolean offPeak, Instant updatedAt) {
		return new HalfHourFlow(serial + "_" + period.start(), serial, period.start(),
				GridDay.round(period.kwh(EnergyFlowType.SOLAR_TO_HOME)),
				GridDay.round(period.kwh(EnergyFlowType.SOLAR_TO_BATTERY)),
				GridDay.round(period.kwh(EnergyFlowType.SOLAR_TO_GRID)),
				GridDay.round(period.kwh(EnergyFlowType.BATTERY_TO_HOME)),
				GridDay.round(period.kwh(EnergyFlowType.BATTERY_TO_GRID)),
				GridDay.round(period.kwh(EnergyFlowType.GRID_TO_HOME)),
				GridDay.round(period.kwh(EnergyFlowType.GRID_TO_BATTERY)),
				offPeak, updatedAt);
	}

	double importKwh() {
		return GridDay.round(gridToHomeKwh + gridToBatteryKwh);
	}

	double exportKwh() {
		return GridDay.round(solarToGridKwh + batteryToGridKwh);
	}

}
