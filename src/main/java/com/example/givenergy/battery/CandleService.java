package com.example.givenergy.battery;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.example.givenergy.GivEnergyProperties;

import org.springframework.stereotype.Service;

/**
 * Builds state-of-charge candles and power traces for the web UI. Past days come from {@code batterySummaries}; the
 * current day and the hourly day view are computed from {@code batteryReadings}, since today's summary isn't written
 * until midnight.
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

	public CandleChart candles(String serial, CandleRange range, LocalDate anchor, boolean compare) {
		List<Candle> candles = candlesFor(serial, range, anchor, false);
		CandleChart comparison = null;
		if (compare) {
			List<Candle> earlier = candlesFor(serial, range, anchor, true);
			comparison = new CandleChart(range, bucket(range), zone.getId(), earlier, charged(earlier), null);
		}
		return new CandleChart(range, bucket(range), zone.getId(), candles, charged(candles), comparison);
	}

	/**
	 * Power and temperature samples. Day is every stored reading over the last 24 hours; week is one averaged point
	 * per hour. Longer ranges are too dense for a line chart.
	 */
	public TraceChart traces(String serial, CandleRange range) {
		if (range != CandleRange.DAY && range != CandleRange.WEEK) {
			throw new IllegalArgumentException("traces are available for day and week");
		}
		ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
		Instant from = range == CandleRange.DAY ? now.minusHours(24).toInstant() : now.minusDays(7).toInstant();
		List<BatteryReading> samples = readings.findByInverterSerialAndTimeInRange(serial, from,
				now.toInstant().plusSeconds(1));
		if (range == CandleRange.DAY) {
			List<TracePoint> points = samples.stream()
					.sorted(Comparator.comparing(BatteryReading::time))
					.map(TracePoint::from)
					.toList();
			return new TraceChart(range, "reading", points);
		}
		Map<Instant, List<BatteryReading>> byHour = samples.stream()
				.collect(Collectors.groupingBy(r -> r.time().truncatedTo(ChronoUnit.HOURS), TreeMap::new,
						Collectors.toList()));
		List<TracePoint> points = byHour.entrySet().stream()
				.map(entry -> TracePoint.average(entry.getKey(), entry.getValue()))
				.toList();
		return new TraceChart(range, "hour", points);
	}

	private List<Candle> candlesFor(String serial, CandleRange range, LocalDate anchor, boolean yearEarlier) {
		if (range == CandleRange.DAY) {
			return hourly(serial, anchor, yearEarlier);
		}
		LocalDate today = LocalDate.now(clock.withZone(zone));
		boolean anchored = anchor != null;
		LocalDate basis = anchor;
		if (yearEarlier) {
			basis = (anchor == null ? today : anchor).minusYears(1);
		}
		DateSpan span = switch (range) {
			case WEEK -> ending(today, basis, 7);
			case MONTH -> anchored ? month(today, basis) : ending(today, basis, 30);
			case YEAR -> ending(today, basis, 365);
			case DAY -> throw new IllegalStateException("day is handled above");
		};
		return daily(serial, span);
	}

	private static String bucket(CandleRange range) {
		return range == CandleRange.DAY ? "hour" : "day";
	}

	private static double charged(List<Candle> candles) {
		return Math.round(candles.stream().filter(c -> c.chargedKwh() != null).mapToDouble(Candle::chargedKwh).sum() * 100)
				/ 100.0;
	}

	/**
	 * Last 24 hours when {@code day} is null, otherwise that local calendar day (today stops at now).
	 * {@code yearEarlier} shifts a rolling window by one year, or the chosen calendar day by one year.
	 */
	private List<Candle> hourly(String serial, LocalDate day, boolean yearEarlier) {
		ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
		if (day == null) {
			ZonedDateTime end = yearEarlier ? now.minusYears(1) : now;
			ZonedDateTime start = end.truncatedTo(ChronoUnit.HOURS).minusHours(23);
			return hourlyBetween(serial, start.toInstant(), end.toInstant().plusSeconds(1));
		}
		LocalDate shown = yearEarlier ? day.minusYears(1) : day;
		ZonedDateTime start = shown.atStartOfDay(zone);
		ZonedDateTime next = shown.plusDays(1).atStartOfDay(zone);
		ZonedDateTime end = next.isAfter(now) ? now : next;
		return hourlyBetween(serial, start.toInstant(), end.toInstant());
	}

	private List<Candle> hourlyBetween(String serial, Instant from, Instant to) {
		Map<Instant, List<BatteryReading>> byHour = readings.findByInverterSerialAndTimeInRange(serial, from, to)
				.stream()
				.collect(Collectors.groupingBy(r -> r.time().truncatedTo(ChronoUnit.HOURS), TreeMap::new,
						Collectors.toList()));
		return byHour.entrySet().stream()
				.map(e -> Candle.of(e.getKey(), e.getKey().plus(1, ChronoUnit.HOURS), e.getValue(), false))
				.toList();
	}

	private DateSpan ending(LocalDate today, LocalDate anchor, int days) {
		LocalDate end = anchor == null || anchor.isAfter(today) ? today : anchor;
		return new DateSpan(end.minusDays(days - 1), end);
	}

	private DateSpan month(LocalDate today, LocalDate anchor) {
		if (anchor == null) {
			return ending(today, null, 30);
		}
		LocalDate end = anchor.isAfter(today) ? today : anchor;
		LocalDate start = end.withDayOfMonth(1);
		LocalDate monthEnd = start.plusMonths(1).minusDays(1);
		if (monthEnd.isAfter(today)) {
			monthEnd = today;
		}
		return new DateSpan(start, monthEnd);
	}

	/** Daily candles for {@code [from, to]} inclusive, with today computed live when the span includes it. */
	private List<Candle> daily(String serial, DateSpan span) {
		LocalDate today = LocalDate.now(clock.withZone(zone));
		List<Candle> candles = new ArrayList<>();
		LocalDate lastFull = span.to().isBefore(today) ? span.to() : today.minusDays(1);
		if (!span.from().isAfter(lastFull)) {
			for (BatterySummary summary : summaries.findByInverterSerialAndDateInRange(serial, span.from(), lastFull)) {
				candles.add(Candle.of(summary, startOf(summary.date()), startOf(summary.date().plusDays(1))));
			}
		}
		if (!span.to().isBefore(today) && !span.from().isAfter(today)) {
			Instant todayStart = startOf(today);
			List<BatteryReading> todays = readings.findByInverterSerialAndTimeInRange(serial, todayStart,
					startOf(today.plusDays(1)));
			if (!todays.isEmpty()) {
				candles.add(Candle.of(todayStart, startOf(today.plusDays(1)), todays, true));
			}
		}
		return candles;
	}

	private Instant startOf(LocalDate date) {
		return date.atStartOfDay(zone).toInstant();
	}

	/**
	 * @param bucket     {@code hour} or {@code day}
	 * @param chargedKwh sum of the candles that carry a charged-energy figure
	 * @param comparison the same window a year earlier, when requested
	 */
	public record CandleChart(CandleRange range, String bucket, String timezone, List<Candle> candles,
			double chargedKwh, CandleChart comparison) {
	}

	/** @param bucket {@code reading} or {@code hour} */
	public record TraceChart(CandleRange range, String bucket, List<TracePoint> points) {
	}

	private record DateSpan(LocalDate from, LocalDate to) {
	}

}
