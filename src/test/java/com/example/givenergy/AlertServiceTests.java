package com.example.givenergy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.example.givenergy.battery.BatteryState;
import com.example.givenergy.battery.BatteryStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertServiceTests {

	private static final ZoneId LONDON = ZoneId.of("Europe/London");

	private final TariffProperties tariff = new TariffProperties(16.5, 36.48, 6.6, LocalTime.of(23, 30),
			LocalTime.of(5, 30), 0.0);

	private final GivEnergyProperties properties = new GivEnergyProperties(URI.create("https://api.test/v1"), "token",
			null, Duration.ZERO, LONDON);

	@Test
	void lowChargeAlertsOnlyInTheHoursAfterOffPeak() {
		AlertService morning = service(Instant.parse("2026-11-07T06:00:00Z"), 20);
		morning.recordSuccess(status(10, "Normal"));

		assertThat(morning.current()).extracting(AlertService.Alert::code).containsExactly("LOW_SOC");

		AlertService afternoon = service(Instant.parse("2026-11-07T12:00:00Z"), 20);
		afternoon.recordSuccess(status(10, "Normal"));

		assertThat(afternoon.current()).isEmpty();
	}

	@Test
	void aFullBatteryAndANormalInverterStayQuiet() {
		AlertService alerts = service(Instant.parse("2026-11-07T06:00:00Z"), 20);
		alerts.recordSuccess(status(80, "Normal"));

		assertThat(alerts.current()).isEmpty();
	}

	@Test
	void inverterStatusAndPollFailuresAreReported() {
		AlertService alerts = service(Instant.parse("2026-11-07T12:00:00Z"), 20);
		alerts.recordFailure("CE2", "timed out");
		alerts.recordSuccess(status(80, "Fault"));

		assertThat(alerts.current()).extracting(AlertService.Alert::code)
				.containsExactly("INVERTER_STATUS", "POLL_FAILED");
	}

	@Test
	void aSuccessfulPollClearsTheFailure() {
		AlertService alerts = service(Instant.parse("2026-11-07T12:00:00Z"), 0);
		alerts.recordFailure("CE1", "timed out");
		alerts.recordSuccess(status(10, "Normal"));

		assertThat(alerts.current()).isEmpty();
	}

	private AlertService service(Instant now, int lowSoc) {
		return new AlertService(new AlertProperties(lowSoc), tariff, properties, Clock.fixed(now, ZoneOffset.UTC));
	}

	private static BatteryStatus status(int soc, String inverterStatus) {
		return new BatteryStatus("CE1", OffsetDateTime.parse("2026-11-07T06:00:00Z"), soc, BatteryState.IDLE, 0, 0,
				20.0, 0, 0, inverterStatus, new BatteryStatus.PowerNow(0, 0, 0, 0),
				new BatteryStatus.EnergyTotals(0, 0, 0, 0, 0, 0), new BatteryStatus.EnergyTotals(0, 0, 0, 0, 0, 0));
	}

}
