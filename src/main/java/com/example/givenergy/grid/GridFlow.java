package com.example.givenergy.grid;

import java.util.List;

/** Direction of grid energy, and the parts each direction is split into. */
public enum GridFlow {

	EXPORT(List.of("solarToGrid", "batteryToGrid")),

	IMPORT(List.of("gridToHome", "gridToBattery"));

	private final List<String> parts;

	GridFlow(List<String> parts) {
		this.parts = parts;
	}

	public List<String> parts() {
		return parts;
	}

}
