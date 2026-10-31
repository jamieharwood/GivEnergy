package com.example.givenergy.battery;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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

	private final BatteryPoller poller = new BatteryPoller(batteryService, repository,
			Clock.fixed(NOW, ZoneOffset.UTC));

	@Test
	void recordsEachReading() {
		given(batteryService.currentStatus()).willReturn(new BatteryStatus("CE1234",
				OffsetDateTime.parse("2026-10-31T13:55:00Z"), 80, BatteryState.CHARGING, 900, 0, 18.0, 3.4, 1.1));

		poller.poll();

		verify(repository).save(new BatteryReading("CE1234_2026-10-31T13:55:00Z", "CE1234",
				Instant.parse("2026-10-31T13:55:00Z"), NOW, 80, BatteryState.CHARGING, 900, 0, 18.0, 3.4, 1.1));
	}

	@Test
	void failedReadIsNotRecorded() {
		given(batteryService.currentStatus()).willThrow(new IllegalStateException("boom"));

		poller.poll();

		verify(repository, never()).save(any());
	}

	@Test
	void sameReadingTimeGivesSameId() {
		BatteryStatus status = new BatteryStatus("CE1234", OffsetDateTime.parse("2026-10-31T13:55:00+01:00"), 80,
				BatteryState.IDLE, 0, 0, null, 0, 0);

		assertThat(BatteryReading.from(status, NOW).id())
				.isEqualTo(BatteryReading.from(status, NOW.plusSeconds(60)).id())
				.isEqualTo("CE1234_2026-10-31T12:55:00Z");
	}

}
