package com.example.givenergy.grid;

import java.time.LocalDate;
import java.util.Map;

import com.example.givenergy.TariffProperties;

/** One inverter's grid energy for one local day, split into named parts (kWh). */
interface GridDay {

	String id();

	LocalDate date();

	/** Part name to kWh, in display order; the names match {@link GridFlow#parts()}. */
	Map<String, Double> parts();

	/** What the day's energy is worth at the tariff: earned for export, cost for import. */
	double valuePence(TariffProperties tariff);

	/** kWh by tariff rate ({@code offPeak}, {@code peak}); empty when one rate applies to the whole day. */
	default Map<String, Double> kwhByRate() {
		return Map.of();
	}

	/** {@link #valuePence} by tariff rate, keyed like {@link #kwhByRate()}; empty when one rate applies. */
	default Map<String, Double> valuePenceByRate(TariffProperties tariff) {
		return Map.of();
	}

	default boolean sameValuesAs(GridDay other) {
		return parts().equals(other.parts());
	}

	/** The API returns float noise such as 5.0299999999999985; 0.01 kWh is plenty. */
	static double round(double kwh) {
		return Math.round(kwh * 100) / 100.0;
	}

}
