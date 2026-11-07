package com.example.givenergy.battery;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.example.givenergy.AlertService;
import com.example.givenergy.InverterDirectory;
import com.example.givenergy.client.CommunicationDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reads every inverter on a fixed interval, logs it and records it in MongoDB. Disabled when
 * {@code givenergy.poll-interval} is 0.
 */
@Component
@ConditionalOnExpression("T(org.springframework.boot.convert.DurationStyle).detectAndParse('${givenergy.poll-interval:5m}').toMillis() > 0")
public class BatteryPoller {

	private static final Logger log = LoggerFactory.getLogger(BatteryPoller.class);

	private final BatteryService batteryService;

	private final BatteryReadingRepository repository;

	private final InverterDirectory inverters;

	private final AlertService alerts;

	private final Clock clock;

	BatteryPoller(BatteryService batteryService, BatteryReadingRepository repository, InverterDirectory inverters,
			AlertService alerts, Clock clock) {
		this.batteryService = batteryService;
		this.repository = repository;
		this.inverters = inverters;
		this.alerts = alerts;
		this.clock = clock;
	}

	@Scheduled(fixedRateString = "${givenergy.poll-interval:5m}", initialDelayString = "5s")
	public void poll() {
		List<CommunicationDevice.Inverter> devices;
		try {
			devices = inverters.list();
		}
		catch (RuntimeException ex) {
			alerts.recordFailure(null, ex.getMessage());
			log.warn("Failed to list inverters: {}", ex.getMessage());
			return;
		}
		if (devices.isEmpty()) {
			alerts.recordFailure(null, "No inverter found on the GivEnergy account");
			log.warn("No inverter found on the GivEnergy account");
			return;
		}
		for (CommunicationDevice.Inverter inverter : devices) {
			try {
				BatteryStatus status = batteryService.currentStatus(inverter.serial());
				log.info("Battery {} {}%: {} (charge {} W, discharge {} W, solar {} W, house {} W) | today: charged {} kWh, discharged {} kWh",
						status.inverterSerial(), status.stateOfChargePercent(), status.state(),
						status.chargePowerWatts(), status.dischargePowerWatts(), status.power().solarWatts(),
						status.power().consumptionWatts(), status.chargedTodayKwh(), status.dischargedTodayKwh());
				repository.save(BatteryReading.from(status, Instant.now(clock)));
				alerts.recordSuccess(status);
			}
			catch (RuntimeException ex) {
				alerts.recordFailure(inverter.serial(), ex.getMessage());
				log.warn("Failed to read or record battery status for {}: {}", inverter.serial(), ex.getMessage());
			}
		}
	}

}
