package com.example.givenergy.grid;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import com.example.givenergy.AppliedTariff;
import com.example.givenergy.GivEnergyProperties;
import com.example.givenergy.TariffProperties;

import org.springframework.stereotype.Service;

/**
 * Aggregates stored daily grid import or export into bars for the web UI. Each day is valued at the prices stored
 * with it, falling back to the current tariff for documents written before prices were stored.
 */
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

	public GridView view(String serial, GridFlow flow, GridRange range, LocalDate anchor, boolean compare) {
		return aggregate(flow, range, anchor, compare, window -> load(serial, flow, window));
	}

	/** Import cost, export earnings and the standing charge for the same window, and the net of the three. */
	public Bill bill(String serial, GridRange range, LocalDate anchor, boolean compare) {
		LocalDate yesterday = yesterday();
		DateWindow window = RangeWindows.of(range, yesterday, anchor);
		Bill comparison = compare ? billFor(serial, range, window.yearEarlier()) : null;
		Bill primary = billFor(serial, range, window);
		return new Bill(primary.from(), primary.to(), primary.importKwh(), primary.exportKwh(), primary.importGbp(),
				primary.exportGbp(), primary.standingChargeGbp(), primary.netGbp(), primary.days(), comparison);
	}

	private Bill billFor(String serial, GridRange range, DateWindow window) {
		GridView importsView = build(GridFlow.IMPORT, range, window, w -> load(serial, GridFlow.IMPORT, w), null);
		GridView exportsView = build(GridFlow.EXPORT, range, window, w -> load(serial, GridFlow.EXPORT, w), null);
		double standing = standingChargeGbp(serial, window);
		double net = GridDay.round(importsView.amountGbp() + standing - exportsView.amountGbp());
		return new Bill(window.from(), window.to(), importsView.totalKwh(), exportsView.totalKwh(),
				importsView.amountGbp(), exportsView.amountGbp(), standing, net,
				(int) (ChronoUnit.DAYS.between(window.from(), window.to()) + 1), null);
	}

	/**
	 * One standing charge per calendar day in the window. A day uses the charge stored on its import, then its
	 * export, then the current tariff for a day with no stored prices.
	 */
	private double standingChargeGbp(String serial, DateWindow window) {
		Map<LocalDate, AppliedTariff> stamps = new HashMap<>();
		for (GridImport day : imports.findByInverterSerialAndDateInRange(serial, window.from(), window.to())) {
			if (day.appliedTariff() != null) {
				stamps.put(day.date(), day.appliedTariff());
			}
		}
		for (GridExport day : exports.findByInverterSerialAndDateInRange(serial, window.from(), window.to())) {
			if (day.appliedTariff() != null) {
				stamps.putIfAbsent(day.date(), day.appliedTariff());
			}
		}
		double pence = 0;
		for (LocalDate date = window.from(); !date.isAfter(window.to()); date = date.plusDays(1)) {
			AppliedTariff stamp = stamps.get(date);
			pence += stamp != null ? stamp.standingChargePence() : tariff.standingChargePence();
		}
		return Math.round(pence) / 100.0;
	}

	GridView aggregate(GridFlow flow, GridRange range, LocalDate anchor, boolean compare,
			Function<DateWindow, List<? extends GridDay>> load) {
		LocalDate yesterday = yesterday();
		DateWindow window = RangeWindows.of(range, yesterday, anchor);
		GridView comparison = compare ? build(flow, range, window.yearEarlier(), load, null) : null;
		return build(flow, range, window, load, comparison);
	}

	private List<? extends GridDay> load(String serial, GridFlow flow, DateWindow window) {
		return flow == GridFlow.EXPORT
				? exports.findByInverterSerialAndDateInRange(serial, window.from(), window.to())
				: imports.findByInverterSerialAndDateInRange(serial, window.from(), window.to());
	}

	private GridView build(GridFlow flow, GridRange range, DateWindow window,
			Function<DateWindow, List<? extends GridDay>> load, GridView comparison) {
		LocalDate from = window.from();
		LocalDate to = window.to();
		boolean monthly = window.monthly();
		Function<LocalDate, LocalDate> bucketOf = monthly ? d -> d.withDayOfMonth(1) : Function.identity();

		// Every bucket in the range appears, so days with no stored data show as gaps rather than vanishing.
		Map<LocalDate, Bar.Builder> buckets = new TreeMap<>();
		for (LocalDate d = from; !d.isAfter(to); d = monthly ? d.plusMonths(1) : d.plusDays(1)) {
			LocalDate start = bucketOf.apply(d);
			LocalDate end = monthly ? min(start.plusMonths(1).minusDays(1), to) : start;
			buckets.put(start, new Bar.Builder(flow, tariff, start, end));
		}
		List<? extends GridDay> days = load.apply(window);
		Set<AppliedTariff> stamps = new HashSet<>();
		boolean unstamped = false;
		for (GridDay day : days) {
			Bar.Builder bucket = buckets.get(bucketOf.apply(day.date()));
			if (bucket == null) {
				continue;
			}
			bucket.add(day);
			if (day.appliedTariff() == null) {
				unstamped = true;
			}
			else {
				stamps.add(day.appliedTariff());
			}
		}
		boolean mixedTariff = stamps.size() > 1 || (unstamped && !stamps.isEmpty());
		List<Bar> bars = buckets.values().stream().map(Bar.Builder::build).toList();
		Map<String, Double> kwh = sum(bars, Bar::kwh);
		return new GridView(flow, range, monthly ? "month" : "day", from, to, kwh,
				GridDay.round(bars.stream().mapToDouble(Bar::totalKwh).sum()), sum(bars, Bar::kwhByRate),
				GridDay.round(bars.stream().mapToDouble(Bar::amountGbp).sum()), sum(bars, Bar::amountGbpByRate), tariff,
				bars.stream().mapToInt(Bar::days).sum(), bars, selfConsumption(kwh), selfSufficiency(kwh), mixedTariff,
				comparison);
	}

	/** Share of solar generation used on site (home + battery), or null when this view has no solar split. */
	private static Double selfConsumption(Map<String, Double> kwh) {
		if (!kwh.containsKey("solarToHome") || !kwh.containsKey("solarToBattery") || !kwh.containsKey("solarToGrid")) {
			return null;
		}
		double onSite = kwh.get("solarToHome") + kwh.get("solarToBattery");
		double generated = onSite + kwh.get("solarToGrid");
		return generated > 0 ? Math.round(onSite / generated * 1000) / 1000.0 : null;
	}

	/** Share of house consumption that didn't come from the grid, or null when this view isn't the house split. */
	private static Double selfSufficiency(Map<String, Double> kwh) {
		if (!kwh.containsKey("solarToHome") || !kwh.containsKey("batteryToHome") || !kwh.containsKey("gridToHome")) {
			return null;
		}
		double onSite = kwh.get("solarToHome") + kwh.get("batteryToHome");
		double house = onSite + kwh.get("gridToHome");
		return house > 0 ? Math.round(onSite / house * 1000) / 1000.0 : null;
	}

	/** Adds up a per-bar map of kWh or pounds, keeping key order. */
	private static Map<String, Double> sum(List<Bar> bars, Function<Bar, Map<String, Double>> values) {
		Map<String, Double> total = new LinkedHashMap<>();
		bars.forEach(b -> values.apply(b).forEach((k, v) -> total.merge(k, v, Double::sum)));
		total.replaceAll((k, v) -> GridDay.round(v));
		return total;
	}

	private LocalDate yesterday() {
		return LocalDate.now(clock.withZone(zone)).minusDays(1);
	}

	private static LocalDate min(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

	/**
	 * @param from       first day covered (inclusive)
	 * @param to         last day covered (inclusive)
	 * @param kwh        total per part over the range, keyed by {@link GridFlow#parts()}
	 * @param kwhByRate  import only: kWh at the {@code offPeak} and {@code peak} rates
	 * @param amountGbp  earned (export) or cost (import) at the stored or current prices, in pounds
	 * @param amountGbpByRate  import only: cost at the {@code offPeak} and {@code peak} rates, in pounds
	 * @param days       how many days in the range have stored data
	 * @param selfConsumptionRatio  solar used on site divided by solar generation; null for grid views
	 * @param selfSufficiencyRatio  house load not bought from the grid, divided by house load; null for grid views
	 * @param mixedTariff  true when the days in the range were not all priced at one tariff
	 * @param comparison  the same window a year earlier, when requested
	 */
	public record GridView(GridFlow flow, GridRange range, String bucket, LocalDate from, LocalDate to,
			Map<String, Double> kwh, double totalKwh, Map<String, Double> kwhByRate, double amountGbp,
			Map<String, Double> amountGbpByRate, TariffProperties tariff, int days, List<Bar> bars,
			Double selfConsumptionRatio, Double selfSufficiencyRatio, boolean mixedTariff, GridView comparison) {
	}

	/**
	 * @param importGbp          cost of imported energy
	 * @param exportGbp          earnings from exported energy
	 * @param standingChargeGbp  one standing charge for every calendar day in the window
	 * @param netGbp             {@code importGbp + standingChargeGbp - exportGbp}; positive is a cost
	 */
	public record Bill(LocalDate from, LocalDate to, double importKwh, double exportKwh, double importGbp,
			double exportGbp, double standingChargeGbp, double netGbp, int days, Bill comparison) {
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
