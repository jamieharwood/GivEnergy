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

import com.example.givenergy.GivEnergyProperties;
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
import org.springframework.util.StringUtils;

/**
 * Keeps {@code gridExports} and {@code gridImports} up to date with one document per inverter per day, from the
 * inverter's commissioning date through yesterday. Both come from the same half-hourly energy-flows calls, summed
 * into local days; import is also split into the tariff's off-peak and peak windows. Each run re-checks the last few
 * stored days (the cloud can still be filling them in) and fetches everything after; only new or changed days are
 * written.
 */
@Component
public class GridHistorySync {

	private static final Logger log = LoggerFactory.getLogger(GridHistorySync.class);

	private static final List<Integer> FLOW_TYPES = List.of(EnergyFlowType.SOLAR_TO_GRID,
			EnergyFlowType.BATTERY_TO_GRID, EnergyFlowType.GRID_TO_HOME, EnergyFlowType.GRID_TO_BATTERY);

	/**
	 * Days per API call. Each padded request covers CHUNK_DAYS + 2 days of 48 half-hours (50 on the autumn clock
	 * change), which must stay under the endpoint's 1000-bucket limit.
	 */
	static final int CHUNK_DAYS = 18;

	/** How many already-stored days to fetch again in case they were incomplete when first stored. */
	static final int RECHECK_DAYS = 2;

	private final GivEnergyClient client;

	private final GridExportRepository exports;

	private final GridImportRepository imports;

	private final GivEnergyProperties properties;

	private final TariffProperties tariff;

	private final ZoneId zone;

	private final Clock clock;

	private final boolean syncOnStartup;

	GridHistorySync(GivEnergyClient client, GridExportRepository exports, GridImportRepository imports,
			GivEnergyProperties properties, TariffProperties tariff, Clock clock,
			@Value("${givenergy.grid-history.sync-on-startup:true}") boolean syncOnStartup) {
		this.client = client;
		this.exports = exports;
		this.imports = imports;
		this.properties = properties;
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

	/** @return the number of documents written (new or changed) across both collections */
	public synchronized int sync() {
		LocalDate today = LocalDate.now(clock.withZone(zone));
		int written = 0;
		for (CommunicationDevice.Inverter inverter : inverters()) {
			// Start from whichever collection is further behind. An empty one means a full back-fill, as do imports
			// stored before the off-peak/peak split existed (they need re-fetching from half-hourly data).
			Optional<LocalDate> latestExport = exports.findTopByInverterSerialOrderByDateDesc(inverter.serial())
					.map(GridExport::date);
			Optional<LocalDate> latestImport = imports.findTopByInverterSerialOrderByDateDesc(inverter.serial())
					.filter(GridImport::hasRateSplit)
					.map(GridImport::date);
			LocalDate from = latestExport.isEmpty() || latestImport.isEmpty() ? firstDay(inverter, today)
					: min(latestExport.get(), latestImport.get()).minusDays(RECHECK_DAYS - 1);
			written += sync(inverter.serial(), from, today);
		}
		return written;
	}

	/**
	 * Re-fetches every day from {@code from} through yesterday, e.g. after changing the tariff's off-peak window.
	 * @return the number of documents written (new or changed) across both collections
	 */
	public synchronized int syncFrom(LocalDate from) {
		LocalDate today = LocalDate.now(clock.withZone(zone));
		int written = 0;
		for (CommunicationDevice.Inverter inverter : inverters()) {
			written += sync(inverter.serial(), from, today);
		}
		return written;
	}

	/** Fetches and stores whole days in {@code [from, toExclusive)}. */
	int sync(String serial, LocalDate from, LocalDate toExclusive) {
		int fetched = 0;
		int exportsWritten = 0;
		int importsWritten = 0;
		Instant now = Instant.now(clock);
		for (LocalDate chunk = from; chunk.isBefore(toExclusive); chunk = chunk.plusDays(CHUNK_DAYS)) {
			// Saved per chunk, so an interrupted back-fill resumes from the last stored day on the next run.
			Map<LocalDate, List<EnergyFlowPeriod>> days = fetch(serial, chunk, min(chunk.plusDays(CHUNK_DAYS), toExclusive));
			fetched += days.size();
			exportsWritten += saveChanged(days.entrySet().stream()
					.map(day -> toExport(serial, day.getKey(), day.getValue(), now)).toList(), exports);
			importsWritten += saveChanged(days.entrySet().stream()
					.map(day -> toImport(serial, day.getKey(), day.getValue(), now)).toList(), imports);
		}
		log.info("Grid history sync for {} {}..{}: {} days fetched, {} export and {} import days new or changed",
				serial, from, toExclusive.minusDays(1), fetched, exportsWritten, importsWritten);
		return exportsWritten + importsWritten;
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

	private static <T extends GridDay> int saveChanged(List<T> days, MongoRepository<T, String> repository) {
		if (days.isEmpty()) {
			return 0;
		}
		Map<String, T> existing = repository.findAllById(days.stream().map(GridDay::id).toList())
				.stream()
				.collect(Collectors.toMap(GridDay::id, Function.identity()));
		List<T> changed = new ArrayList<>();
		for (T day : days) {
			T stored = existing.get(day.id());
			if (stored == null || !stored.sameValuesAs(day)) {
				changed.add(day);
			}
		}
		repository.saveAll(changed);
		return changed.size();
	}

	private List<CommunicationDevice.Inverter> inverters() {
		String configured = properties.inverterSerial();
		return client.communicationDevices().stream()
				.map(CommunicationDevice::inverter)
				.filter(inv -> inv != null && StringUtils.hasText(inv.serial()))
				.filter(inv -> !StringUtils.hasText(configured) || configured.equals(inv.serial()))
				.toList();
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
