package com.example.givenergy.grid;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Energy exported to the grid by one inverter on one local day. The id is {@code <serial>_<date>}, so each day is
 * stored once; re-fetching a day only rewrites it if the values changed.
 */
@Document("gridExports")
public record GridExport(
		@Id String id,
		String inverterSerial,
		LocalDate date,
		double solarToGridKwh,
		double batteryToGridKwh,
		double exportKwh,
		Instant updatedAt) {

	static GridExport of(String serial, LocalDate date, double solarToGridKwh, double batteryToGridKwh,
			Instant updatedAt) {
		double solar = round(solarToGridKwh);
		double battery = round(batteryToGridKwh);
		return new GridExport(serial + "_" + date, serial, date, solar, battery, round(solar + battery), updatedAt);
	}

	boolean sameValuesAs(GridExport other) {
		return solarToGridKwh == other.solarToGridKwh && batteryToGridKwh == other.batteryToGridKwh;
	}

	/** The API returns float noise such as 5.0299999999999985; 0.01 kWh is plenty. */
	private static double round(double kwh) {
		return Math.round(kwh * 100) / 100.0;
	}

}
