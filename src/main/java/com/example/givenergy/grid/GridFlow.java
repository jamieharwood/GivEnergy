package com.example.givenergy.grid;

import java.util.List;

/** A way of splitting a day's energy into named parts for one chart. */
public enum GridFlow {

	EXPORT(List.of("solarToGrid", "batteryToGrid")),

	IMPORT(List.of("gridToHome", "gridToBattery")),

	/** Solar generation: where each kWh of PV went. */
	SOLAR(List.of("solarToHome", "solarToBattery", "solarToGrid")),

	/** House consumption: where each kWh used in the home came from. */
	HOUSE(List.of("solarToHome", "batteryToHome", "gridToHome"));

	private final List<String> parts;

	GridFlow(List<String> parts) {
		this.parts = parts;
	}

	public List<String> parts() {
		return parts;
	}

}
