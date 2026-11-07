package com.example.givenergy.grid;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.givenergy.AppliedTariff;
import com.example.givenergy.TariffProperties;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Energy exported to the grid by one inverter on one local day. The id is {@code <serial>_<date>}, so each day is
 * stored once; re-fetching a day only rewrites it if the values changed. {@code appliedTariff} is the prices in
 * force when the day was first stored.
 */
@Document("gridExports")
public record GridExport(
		@Id String id,
		String inverterSerial,
		LocalDate date,
		double solarToGridKwh,
		double batteryToGridKwh,
		double exportKwh,
		Instant updatedAt,
		AppliedTariff appliedTariff) implements GridDay {

	static GridExport of(String serial, LocalDate date, double solarToGridKwh, double batteryToGridKwh,
			Instant updatedAt) {
		double solar = GridDay.round(solarToGridKwh);
		double battery = GridDay.round(batteryToGridKwh);
		return new GridExport(serial + "_" + date, serial, date, solar, battery, GridDay.round(solar + battery),
				updatedAt, null);
	}

	@Override
	public GridExport withAppliedTariff(AppliedTariff tariff) {
		return new GridExport(id, inverterSerial, date, solarToGridKwh, batteryToGridKwh, exportKwh, updatedAt, tariff);
	}

	@Override
	public double valuePence(TariffProperties tariff) {
		double pence = appliedTariff != null ? appliedTariff.exportPence() : tariff.exportPence();
		return exportKwh * pence;
	}

	@Override
	public Map<String, Double> parts() {
		Map<String, Double> parts = new LinkedHashMap<>();
		parts.put("solarToGrid", solarToGridKwh);
		parts.put("batteryToGrid", batteryToGridKwh);
		return parts;
	}

}
