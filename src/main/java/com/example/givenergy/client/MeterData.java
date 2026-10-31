package com.example.givenergy.client;

import java.time.OffsetDateTime;

/** Response of {@code GET /inverter/{serial}/meter-data/latest} (energy counters in kWh). */
public record MeterData(OffsetDateTime time, Totals today, Totals total) {

	public record Totals(Battery battery) {
	}

	public record Battery(double charge, double discharge) {
	}

}
