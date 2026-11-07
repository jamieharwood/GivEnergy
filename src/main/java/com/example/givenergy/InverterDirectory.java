package com.example.givenergy;

import java.util.List;

import com.example.givenergy.client.CommunicationDevice;
import com.example.givenergy.client.GivEnergyClient;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * The inverters on the account. A configured serial limits the app to that one; otherwise every communication
 * device is used. The list is cached until a grid sync refreshes it, so the battery poll doesn't call the account
 * endpoint on every reading.
 */
@Service
public class InverterDirectory {

	private final GivEnergyClient client;

	private final GivEnergyProperties properties;

	private volatile List<CommunicationDevice.Inverter> cached = List.of();

	private volatile boolean loaded;

	public InverterDirectory(GivEnergyClient client, GivEnergyProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	public List<CommunicationDevice.Inverter> list() {
		if (!loaded) {
			refresh();
		}
		return cached;
	}

	public synchronized void refresh() {
		String configured = properties.inverterSerial();
		cached = client.communicationDevices().stream()
				.map(CommunicationDevice::inverter)
				.filter(inv -> inv != null && StringUtils.hasText(inv.serial()))
				.filter(inv -> !StringUtils.hasText(configured) || configured.equals(inv.serial()))
				.toList();
		loaded = true;
	}

	/**
	 * The serial a request asked for, or the configured one, or the first on the account.
	 */
	public String resolve(String requested) {
		if (StringUtils.hasText(requested)) {
			return requested.trim();
		}
		if (StringUtils.hasText(properties.inverterSerial())) {
			return properties.inverterSerial();
		}
		List<CommunicationDevice.Inverter> inverters = list();
		if (inverters.isEmpty()) {
			throw new IllegalStateException(
					"No inverter found on the GivEnergy account; set givenergy.inverter-serial");
		}
		return inverters.getFirst().serial();
	}

}
