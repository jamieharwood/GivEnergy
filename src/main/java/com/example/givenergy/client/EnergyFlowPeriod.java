package com.example.givenergy.client;

import java.time.LocalDate;
import java.util.Map;

/**
 * One bucket from {@code POST /inverter/{serial}/energy-flows}.
 *
 * @param date        the bucket's start date (local to the inverter's account)
 * @param kwhByType   energy in kWh keyed by flow type, see {@link EnergyFlowType}
 */
public record EnergyFlowPeriod(LocalDate date, Map<Integer, Double> kwhByType) {

	public double kwh(int type) {
		return kwhByType.getOrDefault(type, 0.0);
	}

}
