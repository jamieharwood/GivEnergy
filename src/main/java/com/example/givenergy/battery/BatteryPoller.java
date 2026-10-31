package com.example.givenergy.battery;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reads the battery status on a fixed interval, logs it and records it in MongoDB. Disabled when
 * {@code givenergy.poll-interval} is 0.
 */
@Component
@ConditionalOnExpression("T(org.springframework.boot.convert.DurationStyle).detectAndParse('${givenergy.poll-interval:5m}').toMillis() > 0")
public class BatteryPoller {

	private static final Logger log = LoggerFactory.getLogger(BatteryPoller.class);

	private final BatteryService batteryService;

	private final BatteryReadingRepository repository;

	private final Clock clock;

	BatteryPoller(BatteryService batteryService, BatteryReadingRepository repository, Clock clock) {
		this.batteryService = batteryService;
		this.repository = repository;
		this.clock = clock;
	}

	@Scheduled(fixedRateString = "${givenergy.poll-interval:5m}", initialDelayString = "5s")
	public void poll() {
		try {
			BatteryStatus s = batteryService.currentStatus();
			log.info("Battery {}%: {} (charge {} W, discharge {} W) | today: charged {} kWh, discharged {} kWh",
					s.stateOfChargePercent(), s.state(), s.chargePowerWatts(), s.dischargePowerWatts(),
					s.chargedTodayKwh(), s.dischargedTodayKwh());
			repository.save(BatteryReading.from(s, Instant.now(clock)));
		}
		catch (RuntimeException ex) {
			log.warn("Failed to read or record battery status: {}", ex.getMessage());
		}
	}

}
