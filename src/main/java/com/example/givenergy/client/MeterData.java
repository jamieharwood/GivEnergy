package com.example.givenergy.client;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Response of {@code GET /inverter/{serial}/meter-data/latest} (energy counters in kWh). */
public record MeterData(OffsetDateTime time, Totals today, Totals total) {

	public record Totals(Double solar, Grid grid, Battery battery, Double consumption) {
	}

	public record Grid(@JsonProperty("import") Double imported, @JsonProperty("export") Double exported) {
	}

	public record Battery(double charge, double discharge) {
	}

}
