package com.example.givenergy.grid;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.example.givenergy.AppliedTariff;
import com.example.givenergy.TariffProperties;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Energy imported from the grid by one inverter on one local day, split into what went straight to the house and
 * what charged the battery. {@code offPeakImportKwh} is the part of the total imported during the tariff's off-peak
 * window (the rest is peak); it is null in documents stored before the split existed, which the sync re-fetches.
 * {@code appliedTariff} is the prices in force when the day was first stored. The id is {@code <serial>_<date>}.
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
		Instant updatedAt,
		AppliedTariff appliedTariff) implements GridDay {

	static GridImport of(String serial, LocalDate date, double gridToHomeKwh, double gridToBatteryKwh,
			double offPeakImportKwh, Instant updatedAt) {
		double home = GridDay.round(gridToHomeKwh);
		double battery = GridDay.round(gridToBatteryKwh);
		double total = GridDay.round(home + battery);
		return new GridImport(serial + "_" + date, serial, date, home, battery, total,
				Math.min(total, GridDay.round(offPeakImportKwh)), updatedAt, null);
	}

	@Override
	public GridImport withAppliedTariff(AppliedTariff tariff) {
		return new GridImport(id, inverterSerial, date, gridToHomeKwh, gridToBatteryKwh, importKwh, offPeakImportKwh,
				updatedAt, tariff);
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
		double offPeak = appliedTariff != null ? appliedTariff.importOffPeakPence() : tariff.importOffPeakPence();
		double peak = appliedTariff != null ? appliedTariff.importPeakPence() : tariff.importPeakPence();
		Map<String, Double> rates = new LinkedHashMap<>();
		rates.put("offPeak", offPeakKwh() * offPeak);
		rates.put("peak", peakImportKwh() * peak);
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
