package com.example.givenergy.grid;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.givenergy.client.EnergyFlowPeriod;
import com.example.givenergy.client.EnergyFlowType;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * All seven energy flows for one inverter on one local day. Solar generation and house supply are derived from
 * these; grid import and export stay in their own collections so existing history is unchanged. The id is
 * {@code <serial>_<date>}.
 */
@Document("energyBalances")
public record EnergyBalance(
		@Id String id,
		String inverterSerial,
		LocalDate date,
		double solarToHomeKwh,
		double solarToBatteryKwh,
		double solarToGridKwh,
		double batteryToHomeKwh,
		double batteryToGridKwh,
		double gridToHomeKwh,
		double gridToBatteryKwh,
		Instant updatedAt) {

	static EnergyBalance of(String serial, LocalDate date, Iterable<EnergyFlowPeriod> halfHours, Instant updatedAt) {
		return new EnergyBalance(serial + "_" + date, serial, date,
				sum(halfHours, EnergyFlowType.SOLAR_TO_HOME),
				sum(halfHours, EnergyFlowType.SOLAR_TO_BATTERY),
				sum(halfHours, EnergyFlowType.SOLAR_TO_GRID),
				sum(halfHours, EnergyFlowType.BATTERY_TO_HOME),
				sum(halfHours, EnergyFlowType.BATTERY_TO_GRID),
				sum(halfHours, EnergyFlowType.GRID_TO_HOME),
				sum(halfHours, EnergyFlowType.GRID_TO_BATTERY),
				updatedAt);
	}

	private static double sum(Iterable<EnergyFlowPeriod> halfHours, int type) {
		double total = 0;
		for (EnergyFlowPeriod period : halfHours) {
			total += period.kwh(type);
		}
		return GridDay.round(total);
	}

	Map<String, Double> parts(GridFlow flow) {
		Map<String, Double> parts = new LinkedHashMap<>();
		switch (flow) {
			case SOLAR -> {
				parts.put("solarToHome", solarToHomeKwh);
				parts.put("solarToBattery", solarToBatteryKwh);
				parts.put("solarToGrid", solarToGridKwh);
			}
			case HOUSE -> {
				parts.put("solarToHome", solarToHomeKwh);
				parts.put("batteryToHome", batteryToHomeKwh);
				parts.put("gridToHome", gridToHomeKwh);
			}
			case EXPORT, IMPORT -> throw new IllegalArgumentException(flow + " is not an energy-balance view");
		}
		return parts;
	}

	boolean sameValuesAs(EnergyBalance other) {
		return solarToHomeKwh == other.solarToHomeKwh && solarToBatteryKwh == other.solarToBatteryKwh
				&& solarToGridKwh == other.solarToGridKwh && batteryToHomeKwh == other.batteryToHomeKwh
				&& batteryToGridKwh == other.batteryToGridKwh && gridToHomeKwh == other.gridToHomeKwh
				&& gridToBatteryKwh == other.gridToBatteryKwh;
	}

}
