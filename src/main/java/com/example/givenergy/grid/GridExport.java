package com.example.givenergy.grid;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.givenergy.TariffProperties;

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
		Instant updatedAt) implements GridDay {

	static GridExport of(String serial, LocalDate date, double solarToGridKwh, double batteryToGridKwh,
			Instant updatedAt) {
		double solar = GridDay.round(solarToGridKwh);
		double battery = GridDay.round(batteryToGridKwh);
		return new GridExport(serial + "_" + date, serial, date, solar, battery, GridDay.round(solar + battery),
				updatedAt);
	}

	@Override
	public double valuePence(TariffProperties tariff) {
		return exportKwh * tariff.exportPence();
	}

	@Override
	public Map<String, Double> parts() {
		Map<String, Double> parts = new LinkedHashMap<>();
		parts.put("solarToGrid", solarToGridKwh);
		parts.put("batteryToGrid", batteryToGridKwh);
		return parts;
	}

}
