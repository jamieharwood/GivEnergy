package com.example.givenergy.client;

/** Flow type ids used by the energy-flows endpoint. */
public final class EnergyFlowType {

	public static final int SOLAR_TO_HOME = 0;

	public static final int SOLAR_TO_BATTERY = 1;

	public static final int SOLAR_TO_GRID = 2;

	public static final int GRID_TO_HOME = 3;

	public static final int GRID_TO_BATTERY = 4;

	public static final int BATTERY_TO_HOME = 5;

	public static final int BATTERY_TO_GRID = 6;

	private EnergyFlowType() {
	}

}
