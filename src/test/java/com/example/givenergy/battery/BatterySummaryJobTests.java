package com.example.givenergy.battery;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.example.givenergy.GivEnergyProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BatterySummaryJobTests {

	private static final ZoneId LONDON = ZoneId.of("Europe/London");

	// 00:00 BST on 1 Oct 2026 is 23:00 UTC on 30 Sep
	private static final Instant MIDNIGHT = Instant.parse("2026-09-30T23:00:00Z");

	private final BatteryReadingRepository readings = mock(BatteryReadingRepository.class);

	private final BatterySummaryRepository summaries = mock(BatterySummaryRepository.class);

	private final BatterySummaryJob job = new BatterySummaryJob(readings, summaries,
			new GivEnergyProperties(URI.create("https://api.test/v1"), "token", null, Duration.ZERO, LONDON),
			Clock.fixed(MIDNIGHT, LONDON));

	@Test
	void summarisesYesterdayInLocalTime() {
		Instant from = Instant.parse("2026-09-29T23:00:00Z");
		given(readings.findByTimeInRange(from, MIDNIGHT)).willReturn(List.of(
				reading("CE1", "2026-09-30T06:00:00Z", 40, 0, 300, 0.0, 0.8),
				reading("CE1", "2026-09-30T08:00:00Z", 15, 0, 2500, 0.0, 3.1),
				reading("CE1", "2026-09-30T13:00:00Z", 100, 2900, 0, 6.2, 3.1),
				reading("CE1", "2026-09-30T15:00:00Z", 100, 0, 0, 6.2, 3.1),
				reading("CE1", "2026-09-30T22:55:00Z", 70, 0, 400, 6.4, 5.0)));

		job.summariseYesterday();

		verify(summaries).saveAll(List.of(new BatterySummary("CE1_2026-09-30", "CE1", LocalDate.of(2026, 9, 30),
				"Europe/London", 5, 40, 70, 15, Instant.parse("2026-09-30T08:00:00Z"), 100,
				Instant.parse("2026-09-30T13:00:00Z"), 2900, 2500, 6.4, 5.0, MIDNIGHT)));
	}

	@Test
	void oneSummaryPerInverter() {
		given(readings.findByTimeInRange(any(), any())).willReturn(List.of(
				reading("CE2", "2026-09-30T10:00:00Z", 50, 0, 0, 1, 1),
				reading("CE1", "2026-09-30T10:00:00Z", 60, 0, 0, 2, 2)));

		assertThat(job.summarise(LocalDate.of(2026, 9, 30))).extracting(BatterySummary::id)
				.containsExactly("CE1_2026-09-30", "CE2_2026-09-30");
	}

	@Test
	void noReadingsNoSummary() {
		given(readings.findByTimeInRange(any(), any())).willReturn(List.of());

		job.summariseYesterday();

		verify(summaries, never()).saveAll(any());
	}

	private static BatteryReading reading(String serial, String time, int soc, int chargeW, int dischargeW,
			double chargedKwh, double dischargedKwh) {
		Instant t = Instant.parse(time);
		return new BatteryReading(serial + "_" + t, serial, t, t, soc, BatteryState.IDLE, chargeW, dischargeW, null,
				chargedKwh, dischargedKwh);
	}

}
