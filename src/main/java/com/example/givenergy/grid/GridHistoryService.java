package com.example.givenergy.grid;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import com.example.givenergy.GivEnergyProperties;
import com.example.givenergy.TariffProperties;

import org.springframework.stereotype.Service;

/** Aggregates stored daily grid import or export into bars for the web UI, valued at the configured tariff. */
@Service
public class GridHistoryService {

	private final GridExportRepository exports;

	private final GridImportRepository imports;

	private final TariffProperties tariff;

	private final ZoneId zone;

	private final Clock clock;

	GridHistoryService(GridExportRepository exports, GridImportRepository imports, GivEnergyProperties properties,
			TariffProperties tariff, Clock clock) {
		this.exports = exports;
		this.imports = imports;
		this.tariff = tariff;
		this.zone = properties.timezone();
		this.clock = clock;
	}

	public GridView view(GridFlow flow, GridRange range) {
		LocalDate to = LocalDate.now(clock.withZone(zone)).minusDays(1);
		LocalDate from = switch (range) {
			case YESTERDAY -> to;
			case WEEK -> to.minusDays(6);
			case MONTH -> to.minusDays(29);
			case YEAR -> to.withDayOfMonth(1).minusMonths(11);
		};
		boolean monthly = range == GridRange.YEAR;
		Function<LocalDate, LocalDate> bucketOf = monthly ? d -> d.withDayOfMonth(1) : Function.identity();

		// Every bucket in the range appears, so days with no stored data show as gaps rather than vanishing.
		Map<LocalDate, Bar.Builder> buckets = new TreeMap<>();
		for (LocalDate d = from; !d.isAfter(to); d = monthly ? d.plusMonths(1) : d.plusDays(1)) {
			LocalDate start = bucketOf.apply(d);
			LocalDate end = monthly ? min(start.plusMonths(1).minusDays(1), to) : start;
			buckets.put(start, new Bar.Builder(flow, tariff, start, end));
		}
		List<? extends GridDay> days = flow == GridFlow.EXPORT ? exports.findByDateInRange(from, to)
				: imports.findByDateInRange(from, to);
		for (GridDay day : days) {
			buckets.get(bucketOf.apply(day.date())).add(day);
		}
		List<Bar> bars = buckets.values().stream().map(Bar.Builder::build).toList();
		return new GridView(flow, range, monthly ? "month" : "day", from, to, sum(bars, Bar::kwh),
				GridDay.round(bars.stream().mapToDouble(Bar::totalKwh).sum()), sum(bars, Bar::kwhByRate),
				GridDay.round(bars.stream().mapToDouble(Bar::amountGbp).sum()), sum(bars, Bar::amountGbpByRate), tariff,
				bars.stream().mapToInt(Bar::days).sum(), bars);
	}

	/** Adds up a per-bar map of kWh or pounds, keeping key order. */
	private static Map<String, Double> sum(List<Bar> bars, Function<Bar, Map<String, Double>> values) {
		Map<String, Double> total = new LinkedHashMap<>();
		bars.forEach(b -> values.apply(b).forEach((k, v) -> total.merge(k, v, Double::sum)));
		total.replaceAll((k, v) -> GridDay.round(v));
		return total;
	}

	private static LocalDate min(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

	/**
	 * @param from       first day covered (inclusive)
	 * @param to         last day covered (inclusive)
	 * @param kwh        total per part over the range, keyed by {@link GridFlow#parts()}
	 * @param kwhByRate  import only: kWh at the {@code offPeak} and {@code peak} rates
	 * @param amountGbp  earned (export) or cost (import) at {@code tariff}, in pounds
	 * @param amountGbpByRate  import only: cost at the {@code offPeak} and {@code peak} rates, in pounds
	 * @param days       how many days in the range have stored data
	 */
	public record GridView(GridFlow flow, GridRange range, String bucket, LocalDate from, LocalDate to,
			Map<String, Double> kwh, double totalKwh, Map<String, Double> kwhByRate, double amountGbp,
			Map<String, Double> amountGbpByRate, TariffProperties tariff, int days, List<Bar> bars) {
	}

	/**
	 * @param start      first day of the bar (inclusive)
	 * @param end        last day of the bar (inclusive)
	 * @param kwh        total per part, keyed by {@link GridFlow#parts()}
	 * @param kwhByRate  import only: kWh at the {@code offPeak} and {@code peak} rates
	 * @param amountGbp  earned (export) or cost (import), in pounds; for import, the sum of {@code amountGbpByRate}
	 * @param amountGbpByRate  import only: cost at the {@code offPeak} and {@code peak} rates, in pounds
	 * @param days       how many of its days have stored data; 0 means no data
	 */
	public record Bar(LocalDate start, LocalDate end, Map<String, Double> kwh, double totalKwh,
			Map<String, Double> kwhByRate, double amountGbp, Map<String, Double> amountGbpByRate, int days) {

		static final class Builder {

			private final TariffProperties tariff;

			private final LocalDate start;

			private final LocalDate end;

			private final Map<String, Double> kwh = new LinkedHashMap<>();

			private final Map<String, Double> kwhByRate = new LinkedHashMap<>();

			private final Map<String, Double> penceByRate = new LinkedHashMap<>();

			private double pence;

			private int days;

			Builder(GridFlow flow, TariffProperties tariff, LocalDate start, LocalDate end) {
				this.tariff = tariff;
				this.start = start;
				this.end = end;
				flow.parts().forEach(part -> kwh.put(part, 0.0));
				if (flow == GridFlow.IMPORT) {
					kwhByRate.put("offPeak", 0.0);
					kwhByRate.put("peak", 0.0);
					penceByRate.put("offPeak", 0.0);
					penceByRate.put("peak", 0.0);
				}
			}

			void add(GridDay day) {
				day.parts().forEach((part, value) -> kwh.merge(part, value, Double::sum));
				day.kwhByRate().forEach((rate, value) -> kwhByRate.merge(rate, value, Double::sum));
				day.valuePenceByRate(tariff).forEach((rate, value) -> penceByRate.merge(rate, value, Double::sum));
				pence += day.valuePence(tariff);
				days++;
			}

			Bar build() {
				Map<String, Double> rounded = new LinkedHashMap<>();
				kwh.forEach((part, value) -> rounded.put(part, GridDay.round(value)));
				Map<String, Double> byRate = new LinkedHashMap<>();
				kwhByRate.forEach((rate, value) -> byRate.put(rate, GridDay.round(value)));
				Map<String, Double> gbpByRate = new LinkedHashMap<>();
				penceByRate.forEach((rate, value) -> gbpByRate.put(rate, Math.round(value) / 100.0));
				// Total the rounded rate amounts so the shown off-peak and peak costs always add up to it.
				double gbp = gbpByRate.isEmpty() ? pence / 100
						: gbpByRate.values().stream().mapToDouble(Double::doubleValue).sum();
				return new Bar(start, end, rounded,
						GridDay.round(rounded.values().stream().mapToDouble(Double::doubleValue).sum()), byRate,
						GridDay.round(gbp), gbpByRate, days);
			}

		}

	}

}
