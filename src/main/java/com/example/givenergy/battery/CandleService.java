package com.example.givenergy.battery;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.example.givenergy.GivEnergyProperties;

import org.springframework.stereotype.Service;

/**
 * Builds state-of-charge candles for the web UI. Past days come from {@code batterySummaries}; the current day and
 * the hourly day view are computed from {@code batteryReadings}, since today's summary isn't written until midnight.
 */
@Service
public class CandleService {

	private final BatteryReadingRepository readings;

	private final BatterySummaryRepository summaries;

	private final ZoneId zone;

	private final Clock clock;

	CandleService(BatteryReadingRepository readings, BatterySummaryRepository summaries,
			GivEnergyProperties properties, Clock clock) {
		this.readings = readings;
		this.summaries = summaries;
		this.zone = properties.timezone();
		this.clock = clock;
	}

	public CandleChart candles(CandleRange range) {
		return switch (range) {
			case DAY -> new CandleChart(range, "hour", zone.getId(), hourly());
			case WEEK -> new CandleChart(range, "day", zone.getId(), daily(7));
			case YEAR -> new CandleChart(range, "day", zone.getId(), daily(365));
		};
	}

	/** Hourly candles covering the last 24 hours, including the current partial hour. */
	private List<Candle> hourly() {
		ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
		Instant from = now.truncatedTo(ChronoUnit.HOURS).minusHours(23).toInstant();
		Map<Instant, List<BatteryReading>> byHour = readings.findByTimeInRange(from, now.toInstant().plusSeconds(1))
				.stream()
				.collect(Collectors.groupingBy(r -> r.time().truncatedTo(ChronoUnit.HOURS), TreeMap::new,
						Collectors.toList()));
		return byHour.entrySet().stream()
				.map(e -> Candle.of(e.getKey(), e.getKey().plus(1, ChronoUnit.HOURS), e.getValue(), false))
				.toList();
	}

	/** Daily candles for the last {@code days} days, ending with today so far. */
	private List<Candle> daily(int days) {
		LocalDate today = LocalDate.now(clock.withZone(zone));
		List<Candle> candles = new ArrayList<>();
		for (BatterySummary summary : summaries.findByDateInRange(today.minusDays(days - 1), today.minusDays(1))) {
			candles.add(Candle.of(summary, startOf(summary.date()), startOf(summary.date().plusDays(1))));
		}
		Instant todayStart = startOf(today);
		Instant tomorrowStart = startOf(today.plusDays(1));
		List<BatteryReading> todays = readings.findByTimeInRange(todayStart, tomorrowStart);
		if (!todays.isEmpty()) {
			candles.add(Candle.of(todayStart, tomorrowStart, todays, true));
		}
		return candles;
	}

	private Instant startOf(LocalDate date) {
		return date.atStartOfDay(zone).toInstant();
	}

	/** @param bucket {@code hour} or {@code day} */
	public record CandleChart(CandleRange range, String bucket, String timezone, List<Candle> candles) {
	}

}
