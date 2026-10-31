package com.example.givenergy.client;

import java.time.OffsetDateTime;

/**
 * Response of {@code GET /inverter/{serial}/system-data/latest} (live readings).
 * Only the fields this application uses are mapped.
 */
public record SystemData(OffsetDateTime time, String status, Battery battery, Solar solar, Grid grid,
		Integer consumption) {

	/**
	 * @param percent state of charge, 0–100
	 * @param power   watts; positive = discharging, negative = charging
	 */
	public record Battery(int percent, int power, Double temperature) {
	}

	public record Solar(Integer power) {
	}

	/** @param power watts; positive = exporting, negative = importing */
	public record Grid(Integer power) {
	}

}
