package com.example.givenergy.battery;

import com.example.givenergy.InverterDirectory;
import com.example.givenergy.client.GivEnergyClient;
import com.example.givenergy.client.MeterData;
import com.example.givenergy.client.SystemData;

import org.springframework.stereotype.Service;

@Service
public class BatteryService {

	private final GivEnergyClient client;

	private final InverterDirectory inverters;

	public BatteryService(GivEnergyClient client, InverterDirectory inverters) {
		this.client = client;
		this.inverters = inverters;
	}

	/** Live status of the default inverter (the configured serial, or the first on the account). */
	public BatteryStatus currentStatus() {
		return currentStatus(null);
	}

	/** Live status of {@code requested}, or of the default inverter when it is blank. */
	public BatteryStatus currentStatus(String requested) {
		String serial = inverters.resolve(requested);
		SystemData system = client.latestSystemData(serial);
		MeterData meter = client.latestMeterData(serial);
		return toStatus(serial, system, meter);
	}

	static BatteryStatus toStatus(String serial, SystemData system, MeterData meter) {
		SystemData.Battery battery = system.battery();
		int power = battery.power();
		BatteryState state = power > 0 ? BatteryState.DISCHARGING
				: power < 0 ? BatteryState.CHARGING : BatteryState.IDLE;
		int grid = watts(system.grid() == null ? null : system.grid().power());
		BatteryStatus.PowerNow flows = new BatteryStatus.PowerNow(
				watts(system.solar() == null ? null : system.solar().power()),
				Math.max(grid, 0), Math.max(-grid, 0), watts(system.consumption()));
		MeterData.Battery today = meter.today().battery();
		return new BatteryStatus(serial, system.time(), battery.percent(), state,
				Math.max(-power, 0), Math.max(power, 0), battery.temperature(),
				today.charge(), today.discharge(), system.status(), flows,
				energy(meter.today()), energy(meter.total()));
	}

	private static BatteryStatus.EnergyTotals energy(MeterData.Totals totals) {
		if (totals == null) {
			return new BatteryStatus.EnergyTotals(0, 0, 0, 0, 0, 0);
		}
		MeterData.Battery battery = totals.battery();
		return new BatteryStatus.EnergyTotals(orZero(totals.solar()),
				totals.grid() == null ? 0 : orZero(totals.grid().imported()),
				totals.grid() == null ? 0 : orZero(totals.grid().exported()),
				orZero(totals.consumption()),
				battery == null ? 0 : battery.charge(),
				battery == null ? 0 : battery.discharge());
	}

	private static int watts(Integer value) {
		return value == null ? 0 : value;
	}

	private static double orZero(Double value) {
		return value == null ? 0 : value;
	}

}
