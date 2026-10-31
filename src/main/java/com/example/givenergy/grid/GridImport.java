package com.example.givenergy.grid;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.example.givenergy.TariffProperties;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Energy imported from the grid by one inverter on one local day, split into what went straight to the house and
 * what charged the battery. {@code offPeakImportKwh} is the part of the total imported during the tariff's off-peak
 * window (the rest is peak); it is null in documents stored before the split existed, which the sync re-fetches.
 * The id is {@code <serial>_<date>}, so each day is stored once.
 */
@Document("gridImports")
public record GridImport(
		@Id String id,
		String inverterSerial,
		LocalDate date,
		double gridToHomeKwh,
		double gridToBatteryKwh,
		double importKwh,
		Double offPeakImportKwh,
		Instant updatedAt) implements GridDay {

	static GridImport of(String serial, LocalDate date, double gridToHomeKwh, double gridToBatteryKwh,
			double offPeakImportKwh, Instant updatedAt) {
		double home = GridDay.round(gridToHomeKwh);
		double battery = GridDay.round(gridToBatteryKwh);
		double total = GridDay.round(home + battery);
		return new GridImport(serial + "_" + date, serial, date, home, battery, total,
				Math.min(total, GridDay.round(offPeakImportKwh)), updatedAt);
	}

	boolean hasRateSplit() {
		return offPeakImportKwh != null;
	}

	/** Without a stored split, everything counts as peak. */
	private double offPeakKwh() {
		return hasRateSplit() ? offPeakImportKwh : 0;
	}

	double peakImportKwh() {
		return GridDay.round(importKwh - offPeakKwh());
	}

	@Override
	public double valuePence(TariffProperties tariff) {
		return valuePenceByRate(tariff).values().stream().mapToDouble(Double::doubleValue).sum();
	}

	@Override
	public Map<String, Double> valuePenceByRate(TariffProperties tariff) {
		Map<String, Double> rates = new LinkedHashMap<>();
		rates.put("offPeak", offPeakKwh() * tariff.importOffPeakPence());
		rates.put("peak", peakImportKwh() * tariff.importPeakPence());
		return rates;
	}

	@Override
	public Map<String, Double> kwhByRate() {
		Map<String, Double> rates = new LinkedHashMap<>();
		rates.put("offPeak", offPeakKwh());
		rates.put("peak", peakImportKwh());
		return rates;
	}

	@Override
	public boolean sameValuesAs(GridDay other) {
		return GridDay.super.sameValuesAs(other) && other instanceof GridImport o
				&& Objects.equals(offPeakImportKwh, o.offPeakImportKwh);
	}

	@Override
	public Map<String, Double> parts() {
		Map<String, Double> parts = new LinkedHashMap<>();
		parts.put("gridToHome", gridToHomeKwh);
		parts.put("gridToBattery", gridToBatteryKwh);
		return parts;
	}

}
