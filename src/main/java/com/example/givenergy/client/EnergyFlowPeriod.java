package com.example.givenergy.client;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * One bucket from {@code POST /inverter/{serial}/energy-flows}.
 *
 * @param start       the bucket's start, in the account's local time
 * @param kwhByType   energy in kWh keyed by flow type, see {@link EnergyFlowType}
 */
public record EnergyFlowPeriod(LocalDateTime start, Map<Integer, Double> kwhByType) {

	public LocalDate date() {
		return start.toLocalDate();
	}

	public double kwh(int type) {
		return kwhByType.getOrDefault(type, 0.0);
	}

}
