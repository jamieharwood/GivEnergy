package com.example.givenergy.grid;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.givenergy.AppliedTariff;
import com.example.givenergy.GivEnergyProperties;
import com.example.givenergy.InverterDirectory;
import com.example.givenergy.TariffProperties;
import com.example.givenergy.client.CommunicationDevice;
import com.example.givenergy.client.EnergyFlowPeriod;
import com.example.givenergy.client.EnergyFlowType;
import com.example.givenergy.client.GivEnergyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps daily grid import, grid export and the seven-way energy balance up to date, from the inverter's commissioning
 * date through yesterday. All three come from the same half-hourly energy-flows calls. Import is split into the
 * tariff's off-peak and peak windows. Each run re-checks the last few stored days (the cloud can still be filling
 * them in) and fetches everything after; only new or changed days are written. Days keep the prices in force when
 * they were first stored. The most recent {@link #HALF_HOUR_DAYS} days, including today, are also kept as
 * half-hour buckets.
 */
@Component
public class GridHistorySync {

	private static final Logger log = LoggerFactory.getLogger(GridHistorySync.class);

	private static final List<Integer> FLOW_TYPES = List.of(EnergyFlowType.SOLAR_TO_HOME,
			EnergyFlowType.SOLAR_TO_BATTERY, EnergyFlowType.SOLAR_TO_GRID, EnergyFlowType.GRID_TO_HOME,
			EnergyFlowType.GRID_TO_BATTERY, EnergyFlowType.BATTERY_TO_HOME, EnergyFlowType.BATTERY_TO_GRID);

	/**
	 * Days per API call. Each padded request covers CHUNK_DAYS + 2 days of 48 half-hours (50 on the autumn clock
	 * change), which must stay under the endpoint's 1000-bucket limit.
	 */
	static final int CHUNK_DAYS = 18;

	/** How many already-stored days to fetch again in case they were incomplete when first stored. */
	static final int RECHECK_DAYS = 2;

	/** Half-hour buckets kept, including today. Older ones are deleted each sync. */
	static final int HALF_HOUR_DAYS = 7;

	private final GivEnergyClient client;

	private final GridExportRepository exports;

	private final GridImportRepository imports;

	private final EnergyBalanceRepository balances;

	private final HalfHourFlowRepository halfHours;

	private final InverterDirectory inverters;

	private final TariffProperties tariff;

	private final ZoneId zone;

	private final Clock clock;

	private final boolean syncOnStartup;

	GridHistorySync(GivEnergyClient client, GridExportRepository exports, GridImportRepository imports,
			EnergyBalanceRepository balances, HalfHourFlowRepository halfHours, InverterDirectory inverters,
			GivEnergyProperties properties, TariffProperties tariff, Clock clock,
			@Value("${givenergy.grid-history.sync-on-startup:true}") boolean syncOnStartup) {
		this.client = client;
		this.exports = exports;
		this.imports = imports;
		this.balances = balances;
		this.halfHours = halfHours;
		this.inverters = inverters;
		this.tariff = tariff;
		this.zone = properties.timezone();
		this.clock = clock;
		this.syncOnStartup = syncOnStartup;
	}

	/** Back-fills history (first run) or catches up missed days shortly after the app starts. Runs once. */
	@Scheduled(initialDelayString = "${givenergy.grid-history.startup-delay:15s}")
	public void syncAfterStartup() {
		if (syncOnStartup) {
			runSafely();
		}
	}

	/** Fetches the previous day just after midnight, once the cloud has the day's last readings. */
	@Scheduled(cron = "${givenergy.grid-history.cron:0 30 0 * * *}", zone = "${givenergy.timezone:Europe/London}")
	public void syncPreviousDay() {
		runSafely();
	}

	private void runSafely() {
		try {
			sync();
		}
		catch (RuntimeException ex) {
			log.warn("Grid history sync failed: {}", ex.getMessage());
		}
	}

	/**
	 * @return the number of daily documents written (new or changed) across exports, imports and energy balances
	 */
	public synchronized int sync() {
		LocalDate today = LocalDate.now(clock.withZone(zone));
		inverters.refresh();
		int written = 0;
		for (CommunicationDevice.Inverter inverter : inverters.list()) {
			// Start from whichever collection is further behind. An empty one means a full back-fill, as do imports
			// stored before the off-peak/peak split existed (they need re-fetching from half-hourly data).
			Optional<LocalDate> latestExport = exports.findTopByInverterSerialOrderByDateDesc(inverter.serial())
					.map(GridExport::date);
			Optional<LocalDate> latestImport = imports.findTopByInverterSerialOrderByDateDesc(inverter.serial())
					.filter(GridImport::hasRateSplit)
					.map(GridImport::date);
			Optional<LocalDate> latestBalance = balances.findTopByInverterSerialOrderByDateDesc(inverter.serial())
					.map(EnergyBalance::date);
			LocalDate from = latestExport.isEmpty() || latestImport.isEmpty() || latestBalance.isEmpty()
					? firstDay(inverter, today)
					: min(min(latestExport.get(), latestImport.get()), latestBalance.get()).minusDays(RECHECK_DAYS - 1);
			written += sync(inverter.serial(), from, today);
			refreshHalfHours(inverter.serial(), today);
		}
		written += stampMissing();
		return written;
	}

	/**
	 * Re-fetches every day from {@code from} through yesterday, e.g. after changing the tariff's off-peak window.
	 * Stored prices are kept. @return the number of daily documents written
	 */
	public synchronized int syncFrom(LocalDate from) {
		LocalDate today = LocalDate.now(clock.withZone(zone));
		inverters.refresh();
		int written = 0;
		for (CommunicationDevice.Inverter inverter : inverters.list()) {
			written += sync(inverter.serial(), from, today);
			refreshHalfHours(inverter.serial(), today);
		}
		written += stampMissing();
		return written;
	}

	/** Fetches and stores whole days in {@code [from, toExclusive)}. */
	int sync(String serial, LocalDate from, LocalDate toExclusive) {
		int fetched = 0;
		int exportsWritten = 0;
		int importsWritten = 0;
		int balancesWritten = 0;
		Instant now = Instant.now(clock);
		for (LocalDate chunk = from; chunk.isBefore(toExclusive); chunk = chunk.plusDays(CHUNK_DAYS)) {
			// Saved per chunk, so an interrupted back-fill resumes from the last stored day on the next run.
			Map<LocalDate, List<EnergyFlowPeriod>> days = fetch(serial, chunk, min(chunk.plusDays(CHUNK_DAYS), toExclusive));
			fetched += days.size();
			exportsWritten += saveChanged(days.entrySet().stream()
					.map(day -> toExport(serial, day.getKey(), day.getValue(), now)).toList(), exports);
			importsWritten += saveChanged(days.entrySet().stream()
					.map(day -> toImport(serial, day.getKey(), day.getValue(), now)).toList(), imports);
			balancesWritten += saveBalances(days.entrySet().stream()
					.map(day -> EnergyBalance.of(serial, day.getKey(), day.getValue(), now)).toList());
		}
		log.info("Grid history sync for {} {}..{}: {} days fetched, {} export, {} import and {} balance days new or changed",
				serial, from, toExclusive.minusDays(1), fetched, exportsWritten, importsWritten, balancesWritten);
		return exportsWritten + importsWritten + balancesWritten;
	}

	private void refreshHalfHours(String serial, LocalDate today) {
		LocalDate from = today.minusDays(HALF_HOUR_DAYS - 1);
		LocalDate toExclusive = today.plusDays(1);
		Instant now = Instant.now(clock);
		List<HalfHourFlow> buckets = client.halfHourlyEnergyFlows(serial, from.minusDays(1), toExclusive.plusDays(1), FLOW_TYPES)
				.stream()
				.filter(period -> !period.date().isBefore(from) && period.date().isBefore(toExclusive))
				.map(period -> HalfHourFlow.of(serial, period, tariff.isOffPeak(period.start().toLocalTime()), now))
				.toList();
		halfHours.deleteByInverterSerialAndStartBefore(serial, from.atStartOfDay());
		halfHours.saveAll(buckets);
		log.info("Half-hour flows for {}: {} buckets from {} kept", serial, buckets.size(), from);
	}

	/**
	 * Half-hours grouped by local day. The API's edge buckets can be partial, so ask for one extra day either side
	 * and keep only the requested days.
	 */
	private Map<LocalDate, List<EnergyFlowPeriod>> fetch(String serial, LocalDate from, LocalDate toExclusive) {
		return client.halfHourlyEnergyFlows(serial, from.minusDays(1), toExclusive.plusDays(1), FLOW_TYPES)
				.stream()
				.filter(p -> !p.date().isBefore(from) && p.date().isBefore(toExclusive))
				.collect(Collectors.groupingBy(EnergyFlowPeriod::date, TreeMap::new, Collectors.toList()));
	}

	private static GridExport toExport(String serial, LocalDate date, List<EnergyFlowPeriod> halfHours, Instant now) {
		return GridExport.of(serial, date, sum(halfHours, EnergyFlowType.SOLAR_TO_GRID),
				sum(halfHours, EnergyFlowType.BATTERY_TO_GRID), now);
	}

	private GridImport toImport(String serial, LocalDate date, List<EnergyFlowPeriod> halfHours, Instant now) {
		double offPeak = halfHours.stream()
				.filter(p -> tariff.isOffPeak(p.start().toLocalTime()))
				.mapToDouble(p -> p.kwh(EnergyFlowType.GRID_TO_HOME) + p.kwh(EnergyFlowType.GRID_TO_BATTERY))
				.sum();
		return GridImport.of(serial, date, sum(halfHours, EnergyFlowType.GRID_TO_HOME),
				sum(halfHours, EnergyFlowType.GRID_TO_BATTERY), offPeak, now);
	}

	private static double sum(List<EnergyFlowPeriod> halfHours, int type) {
		return halfHours.stream().mapToDouble(p -> p.kwh(type)).sum();
	}

	private <T extends GridDay> int saveChanged(List<T> days, MongoRepository<T, String> repository) {
		if (days.isEmpty()) {
			return 0;
		}
		Map<String, T> existing = repository.findAllById(days.stream().map(GridDay::id).toList())
				.stream()
				.collect(Collectors.toMap(GridDay::id, Function.identity()));
		List<T> changed = new ArrayList<>();
		for (T day : days) {
			T stored = existing.get(day.id());
			T stamped = stamp(day, stored);
			boolean missingPrice = stored != null && stored.appliedTariff() == null;
			if (stored == null || !stored.sameValuesAs(stamped) || missingPrice) {
				changed.add(stamped);
			}
		}
		if (!changed.isEmpty()) {
			repository.saveAll(changed);
		}
		return changed.size();
	}

	@SuppressWarnings("unchecked")
	private <T extends GridDay> T stamp(T day, T stored) {
		AppliedTariff keep = stored != null && stored.appliedTariff() != null
				? stored.appliedTariff() : AppliedTariff.from(tariff);
		return (T) day.withAppliedTariff(keep);
	}

	private int saveBalances(List<EnergyBalance> days) {
		if (days.isEmpty()) {
			return 0;
		}
		Map<String, EnergyBalance> existing = balances.findAllById(days.stream().map(EnergyBalance::id).toList())
				.stream()
				.collect(Collectors.toMap(EnergyBalance::id, Function.identity()));
		List<EnergyBalance> changed = new ArrayList<>();
		for (EnergyBalance day : days) {
			EnergyBalance stored = existing.get(day.id());
			if (stored == null || !stored.sameValuesAs(day)) {
				changed.add(day);
			}
		}
		if (!changed.isEmpty()) {
			balances.saveAll(changed);
		}
		return changed.size();
	}

	/** Freezes the current prices onto days stored before prices were recorded. */
	private int stampMissing() {
		int written = stampMissing(exports.findByAppliedTariffIsNull(), exports);
		written += stampMissing(imports.findByAppliedTariffIsNull(), imports);
		if (written > 0) {
			log.info("Stamped current tariff onto {} previously unpriced days", written);
		}
		return written;
	}

	private <T extends GridDay> int stampMissing(List<T> missing, MongoRepository<T, String> repository) {
		if (missing.isEmpty()) {
			return 0;
		}
		AppliedTariff current = AppliedTariff.from(tariff);
		List<T> stamped = new ArrayList<>();
		for (T day : missing) {
			@SuppressWarnings("unchecked")
			T copy = (T) day.withAppliedTariff(current);
			stamped.add(copy);
		}
		repository.saveAll(stamped);
		return stamped.size();
	}

	private LocalDate firstDay(CommunicationDevice.Inverter inverter, LocalDate today) {
		return inverter.commissionDate() != null
				? inverter.commissionDate().atZoneSameInstant(zone).toLocalDate()
				: today.minusYears(1);
	}

	private static LocalDate min(LocalDate a, LocalDate b) {
		return a.isBefore(b) ? a : b;
	}

}
