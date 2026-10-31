package com.example.givenergy.battery;

import com.example.givenergy.GivEnergyProperties;
import com.example.givenergy.client.CommunicationDevice;
import com.example.givenergy.client.GivEnergyClient;
import com.example.givenergy.client.MeterData;
import com.example.givenergy.client.SystemData;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BatteryService {

	private final GivEnergyClient client;

	private final GivEnergyProperties properties;

	private volatile String discoveredSerial;

	public BatteryService(GivEnergyClient client, GivEnergyProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	public BatteryStatus currentStatus() {
		String serial = inverterSerial();
		SystemData system = client.latestSystemData(serial);
		MeterData meter = client.latestMeterData(serial);
		return toStatus(serial, system, meter);
	}

	static BatteryStatus toStatus(String serial, SystemData system, MeterData meter) {
		SystemData.Battery battery = system.battery();
		int power = battery.power();
		BatteryState state = power > 0 ? BatteryState.DISCHARGING
				: power < 0 ? BatteryState.CHARGING : BatteryState.IDLE;
		MeterData.Battery today = meter.today().battery();
		return new BatteryStatus(serial, system.time(), battery.percent(), state,
				Math.max(-power, 0), Math.max(power, 0), battery.temperature(),
				today.charge(), today.discharge());
	}

	private String inverterSerial() {
		if (StringUtils.hasText(properties.inverterSerial())) {
			return properties.inverterSerial();
		}
		if (discoveredSerial == null) {
			discoveredSerial = client.communicationDevices().stream()
					.map(CommunicationDevice::inverter)
					.filter(inv -> inv != null && StringUtils.hasText(inv.serial()))
					.map(CommunicationDevice.Inverter::serial)
					.findFirst()
					.orElseThrow(() -> new IllegalStateException(
							"No inverter found on the GivEnergy account; set givenergy.inverter-serial"));
		}
		return discoveredSerial;
	}

}
