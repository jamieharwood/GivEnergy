package com.example.givenergy.battery;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.example.givenergy.AlertService;
import com.example.givenergy.InverterDirectory;
import com.example.givenergy.client.CommunicationDevice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BatteryPollerTests {

	private static final Instant NOW = Instant.parse("2026-10-31T14:00:00Z");

	private final BatteryService batteryService = mock(BatteryService.class);

	private final BatteryReadingRepository repository = mock(BatteryReadingRepository.class);

	private final InverterDirectory inverters = mock(InverterDirectory.class);

	private final AlertService alerts = mock(AlertService.class);

	private final BatteryPoller poller = new BatteryPoller(batteryService, repository, inverters, alerts,
			Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void recordsEachReading() {
		given(inverters.list()).willReturn(List.of(new CommunicationDevice.Inverter("CE1234", "AC", null)));
		BatteryStatus status = status(BatteryState.CHARGING, 900, 0);
		given(batteryService.currentStatus("CE1234")).willReturn(status);

		poller.poll();

		verify(repository).save(BatteryReading.from(status, NOW));
		verify(alerts).recordSuccess(status);
	}

	@Test
	void failedReadIsNotRecorded() {
		given(inverters.list()).willReturn(List.of(new CommunicationDevice.Inverter("CE1234", "AC", null)));
		given(batteryService.currentStatus("CE1234")).willThrow(new IllegalStateException("boom"));

		poller.poll();

		verify(repository, never()).save(any());
		verify(alerts).recordFailure("CE1234", "boom");
	}

	@Test
	void sameReadingTimeGivesSameId() {
		BatteryStatus status = new BatteryStatus("CE1234", OffsetDateTime.parse("2026-10-31T13:55:00+01:00"), 80,
				BatteryState.IDLE, 0, 0, null, 0, 0, null, new BatteryStatus.PowerNow(0, 0, 0, 0),
				new BatteryStatus.EnergyTotals(0, 0, 0, 0, 0, 0), new BatteryStatus.EnergyTotals(0, 0, 0, 0, 0, 0));

		assertThat(BatteryReading.from(status, NOW).id())
				.isEqualTo(BatteryReading.from(status, NOW.plusSeconds(60)).id())
				.isEqualTo("CE1234_2026-10-31T12:55:00Z");
	}

	private static BatteryStatus status(BatteryState state, int chargeWatts, int dischargeWatts) {
		return new BatteryStatus("CE1234", OffsetDateTime.parse("2026-10-31T13:55:00Z"), 80, state, chargeWatts,
				dischargeWatts, 18.0, 3.4, 1.1, "Normal", new BatteryStatus.PowerNow(100, 0, 20, 400),
				new BatteryStatus.EnergyTotals(1, 2, 0.3, 4, 3.4, 1.1),
				new BatteryStatus.EnergyTotals(0, 0, 0, 0, 0, 0));
	}

}
