package com.example.givenergy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.example.givenergy.battery.BatteryStatus;

import org.springframework.stereotype.Service;

/** Live alerts derived from the latest successful poll and from poll failures. Nothing is stored. */
@Service
public class AlertService {

	private final AlertProperties alerts;

	private final TariffProperties tariff;

	private final ZoneId zone;

	private final Clock clock;

	private final ConcurrentHashMap<String, BatteryStatus> latest = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, Failure> failures = new ConcurrentHashMap<>();

	public AlertService(AlertProperties alerts, TariffProperties tariff, GivEnergyProperties properties, Clock clock) {
		this.alerts = alerts;
		this.tariff = tariff;
		this.zone = properties.timezone();
		this.clock = clock;
	}

	public void recordSuccess(BatteryStatus status) {
		latest.put(status.inverterSerial(), status);
		failures.remove(status.inverterSerial());
	}

	public void recordFailure(String serial, String message) {
		failures.put(serial == null ? "*" : serial, new Failure(message, Instant.now(clock)));
	}

	public List<Alert> current() {
		List<Alert> active = new ArrayList<>();
		failures.forEach((serial, failure) -> active.add(new Alert("POLL_FAILED",
				(serial.equals("*") ? "The battery poll failed: " : serial + ": the battery poll failed: ")
						+ failure.message(),
				failure.at())));
		LocalTime now = LocalTime.now(clock.withZone(zone));
		boolean chargeWindowMissed = tariff.isJustAfterOffPeak(now);
		latest.values().forEach(status -> {
			String serial = status.inverterSerial();
			if (status.inverterStatus() != null && !status.inverterStatus().equalsIgnoreCase("Normal")) {
				active.add(new Alert("INVERTER_STATUS",
						serial + " inverter status is " + status.inverterStatus(), status.time().toInstant()));
			}
			if (alerts.lowSocPercent() > 0 && chargeWindowMissed
					&& status.stateOfChargePercent() < alerts.lowSocPercent()) {
				active.add(new Alert("LOW_SOC", serial + " is at " + status.stateOfChargePercent()
						+ "% after the off-peak window, below " + alerts.lowSocPercent() + "%",
						status.time().toInstant()));
			}
		});
		active.sort((a, b) -> a.code().compareTo(b.code()));
		return List.copyOf(active);
	}

	public record Alert(String code, String message, Instant at) {
	}

	private record Failure(String message, Instant at) {
	}

}
