package com.example.givenergy.grid;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import com.example.givenergy.GivEnergyProperties;

import org.springframework.stereotype.Service;

/** Aggregates stored daily exports into bars for the web UI. */
@Service
public class GridExportService {

	private final GridExportRepository repository;

	private final ZoneId zone;

	private final Clock clock;

	GridExportService(GridExportRepository repository, GivEnergyProperties properties, Clock clock) {
		this.repository = repository;
		this.zone = properties.timezone();
		this.clock = clock;
	}

	public GridExportView view(GridExportRange range) {
		LocalDate to = LocalDate.now(clock.withZone(zone)).minusDays(1);
		LocalDate from = switch (range) {
			case YESTERDAY -> to;
			case WEEK -> to.minusDays(6);
			case MONTH -> to.minusDays(29);
			case YEAR -> to.withDayOfMonth(1).minusMonths(11);
		};
		boolean monthly = range == GridExportRange.YEAR;
		Function<LocalDate, LocalDate> bucketOf = monthly ? d -> d.withDayOfMonth(1) : Function.identity();

		// Every bucket in the range appears, so days with no stored data show as gaps rather than vanishing.
		Map<LocalDate, Bar.Builder> buckets = new TreeMap<>();
		for (LocalDate d = from; !d.isAfter(to); d = monthly ? d.plusMonths(1).withDayOfMonth(1) : d.plusDays(1)) {
			LocalDate start = bucketOf.apply(d);
			LocalDate end = monthly ? min(start.plusMonths(1).minusDays(1), to) : start;
			buckets.put(start, new Bar.Builder(max(start, from), end));
		}
		for (GridExport day : repository.findByDateInRange(from, to)) {
			buckets.get(bucketOf.apply(day.date())).add(day);
		}
		List<Bar> bars = new ArrayList<>();
		buckets.values().forEach(b -> bars.add(b.build()));
		return new GridExportView(range, monthly ? "month" : "day", from, to,
				round(bars.stream().mapToDouble(Bar::solarToGridKwh).sum()),
				round(bars.stream().mapToDouble(Bar::batteryToGridKwh).sum()),
				round(bars.stream().mapToDouble(Bar::exportKwh).sum()),
				bars.stream().mapToInt(Bar::days).sum(), bars);
	}

	private static double round(double kwh) {
		return Math.round(kwh * 100) / 100.0;
	}

	private static LocalDate min(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

	private static LocalDate max(LocalDate a, LocalDate b) {
		return a.isAfter(b) ? a : b;
	}

	/**
	 * @param from    first day covered (inclusive)
	 * @param to      last day covered (inclusive)
	 * @param days    how many days in the range have stored data
	 */
	public record GridExportView(GridExportRange range, String bucket, LocalDate from, LocalDate to,
			double solarToGridKwh, double batteryToGridKwh, double exportKwh, int days, List<Bar> bars) {
	}

	/**
	 * @param start first day of the bar (inclusive)
	 * @param end   last day of the bar (inclusive)
	 * @param days  how many of its days have stored data; 0 means no data
	 */
	public record Bar(LocalDate start, LocalDate end, double solarToGridKwh, double batteryToGridKwh,
			double exportKwh, int days) {

		static final class Builder {

			private final LocalDate start;

			private final LocalDate end;

			private double solar;

			private double battery;

			private int days;

			Builder(LocalDate start, LocalDate end) {
				this.start = start;
				this.end = end;
			}

			void add(GridExport day) {
				solar += day.solarToGridKwh();
				battery += day.batteryToGridKwh();
				days++;
			}

			Bar build() {
				return new Bar(start, end, round(solar), round(battery), round(solar + battery), days);
			}

		}

	}

}
