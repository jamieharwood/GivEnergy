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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CandleServiceTests {

	private static final ZoneId LONDON = ZoneId.of("Europe/London");

	// 14:30 BST on 30 Sep 2026
	private static final Instant NOW = Instant.parse("2026-09-30T13:30:00Z");

	private final BatteryReadingRepository readings = mock(BatteryReadingRepository.class);

	private final BatterySummaryRepository summaries = mock(BatterySummaryRepository.class);

	private final CandleService service = new CandleService(readings, summaries,
			new GivEnergyProperties(URI.create("https://api.test/v1"), "token", null, Duration.ZERO, LONDON),
			Clock.fixed(NOW, LONDON));

	@Test
	void dayViewIsHourlyCandlesOverTheLast24Hours() {
		given(readings.findByTimeInRange(Instant.parse("2026-09-29T14:00:00Z"), NOW.plusSeconds(1)))
				.willReturn(List.of(reading("2026-09-30T12:05:00Z", 60), reading("2026-09-30T12:55:00Z", 50),
						reading("2026-09-30T12:30:00Z", 70), reading("2026-09-30T13:10:00Z", 52)));

		CandleService.CandleChart chart = service.candles(CandleRange.DAY);

		assertThat(chart.bucket()).isEqualTo("hour");
		assertThat(chart.candles()).containsExactly(
				new Candle(Instant.parse("2026-09-30T12:00:00Z"), Instant.parse("2026-09-30T13:00:00Z"), 60, 70, 50,
						50, 3, null, null),
				new Candle(Instant.parse("2026-09-30T13:00:00Z"), Instant.parse("2026-09-30T14:00:00Z"), 52, 52, 52,
						52, 1, null, null));
	}

	@Test
	void weekViewIsPastSummariesPlusToday() {
		given(summaries.findByDateInRange(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 29)))
				.willReturn(List.of(new BatterySummary("CE1_2026-09-29", "CE1", LocalDate.of(2026, 9, 29),
						"Europe/London", 288, 80, 30, 10, Instant.EPOCH, 100, Instant.EPOCH, 3000, 2500, 9.5, 8.0,
						Instant.EPOCH)));
		given(readings.findByTimeInRange(Instant.parse("2026-09-29T23:00:00Z"), Instant.parse("2026-09-30T23:00:00Z")))
				.willReturn(List.of(reading("2026-09-30T05:00:00Z", 30), reading("2026-09-30T12:00:00Z", 90)));

		List<Candle> candles = service.candles(CandleRange.WEEK).candles();

		assertThat(candles).hasSize(2);
		assertThat(candles.getFirst()).isEqualTo(new Candle(Instant.parse("2026-09-28T23:00:00Z"),
				Instant.parse("2026-09-29T23:00:00Z"), 80, 100, 10, 30, 288, 9.5, 8.0));
		assertThat(candles.getLast()).extracting(Candle::open, Candle::high, Candle::low, Candle::close)
				.containsExactly(30, 90, 30, 90);
	}

	@Test
	void rangeNamesAreCaseInsensitive() {
		assertThat(CandleRange.from(" Year ")).isEqualTo(CandleRange.YEAR);
	}

	private static BatteryReading reading(String time, int soc) {
		Instant t = Instant.parse(time);
		return new BatteryReading("CE1_" + t, "CE1", t, t, soc, BatteryState.IDLE, 0, 0, null, 1.0, 2.0);
	}

}
