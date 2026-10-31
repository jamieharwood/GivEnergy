package com.example.givenergy.grid;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.example.givenergy.GivEnergyProperties;
import com.example.givenergy.client.CommunicationDevice;
import com.example.givenergy.client.EnergyFlowPeriod;
import com.example.givenergy.client.EnergyFlowType;
import com.example.givenergy.client.GivEnergyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Keeps {@code gridExports} up to date with one document per inverter per day, from the inverter's commissioning date
 * through yesterday. Each run re-checks the last few stored days (the cloud can still be filling them in) and fetches
 * everything after; only new or changed days are written.
 */
@Component
public class GridExportSync {

	private static final Logger log = LoggerFactory.getLogger(GridExportSync.class);

	private static final List<Integer> EXPORT_TYPES = List.of(EnergyFlowType.SOLAR_TO_GRID,
			EnergyFlowType.BATTERY_TO_GRID);

	/** Days per API call; a 400-day request takes under 2 s, so this keeps the back-fill to a handful of calls. */
	static final int CHUNK_DAYS = 180;

	/** How many already-stored days to fetch again in case they were incomplete when first stored. */
	static final int RECHECK_DAYS = 2;

	private final GivEnergyClient client;

	private final GridExportRepository repository;

	private final GivEnergyProperties properties;

	private final ZoneId zone;

	private final Clock clock;

	private final boolean syncOnStartup;

	GridExportSync(GivEnergyClient client, GridExportRepository repository, GivEnergyProperties properties,
			Clock clock, @Value("${givenergy.grid-export.sync-on-startup:true}") boolean syncOnStartup) {
		this.client = client;
		this.repository = repository;
		this.properties = properties;
		this.zone = properties.timezone();
		this.clock = clock;
		this.syncOnStartup = syncOnStartup;
	}

	/** Back-fills history (first run) or catches up missed days shortly after the app starts. Runs once. */
	@Scheduled(initialDelayString = "${givenergy.grid-export.startup-delay:15s}")
	public void syncAfterStartup() {
		if (syncOnStartup) {
			runSafely();
		}
	}

	/** Fetches the previous day's export just after midnight, once the cloud has the day's last readings. */
	@Scheduled(cron = "${givenergy.grid-export.cron:0 30 0 * * *}", zone = "${givenergy.timezone:Europe/London}")
	public void syncPreviousDay() {
		runSafely();
	}

	private void runSafely() {
		try {
			sync();
		}
		catch (RuntimeException ex) {
			log.warn("Grid export sync failed: {}", ex.getMessage());
		}
	}

	/** @return the number of days written (new or changed) */
	public synchronized int sync() {
		LocalDate today = LocalDate.now(clock.withZone(zone));
		int written = 0;
		for (CommunicationDevice.Inverter inverter : inverters()) {
			LocalDate from = repository.findTopByInverterSerialOrderByDateDesc(inverter.serial())
					.map(latest -> latest.date().minusDays(RECHECK_DAYS - 1))
					.orElseGet(() -> firstDay(inverter, today));
			written += sync(inverter.serial(), from, today);
		}
		return written;
	}

	/** Fetches and stores whole days in {@code [from, toExclusive)}. */
	int sync(String serial, LocalDate from, LocalDate toExclusive) {
		int fetched = 0;
		int written = 0;
		for (LocalDate chunk = from; chunk.isBefore(toExclusive); chunk = chunk.plusDays(CHUNK_DAYS)) {
			LocalDate chunkEnd = min(chunk.plusDays(CHUNK_DAYS), toExclusive);
			List<GridExport> days = fetch(serial, chunk, chunkEnd);
			fetched += days.size();
			written += saveChanged(days);
		}
		log.info("Grid export sync for {} {}..{}: {} days fetched, {} new or changed", serial, from,
				toExclusive.minusDays(1), fetched, written);
		return written;
	}

	/**
	 * The API's edge buckets can be partial, so ask for one extra day either side and keep only the requested days.
	 */
	private List<GridExport> fetch(String serial, LocalDate from, LocalDate toExclusive) {
		Instant now = Instant.now(clock);
		return client.dailyEnergyFlows(serial, from.minusDays(1), toExclusive.plusDays(1), EXPORT_TYPES)
				.stream()
				.filter(p -> !p.date().isBefore(from) && p.date().isBefore(toExclusive))
				.map(p -> toExport(serial, p, now))
				.toList();
	}

	private static GridExport toExport(String serial, EnergyFlowPeriod period, Instant now) {
		return GridExport.of(serial, period.date(), period.kwh(EnergyFlowType.SOLAR_TO_GRID),
				period.kwh(EnergyFlowType.BATTERY_TO_GRID), now);
	}

	private int saveChanged(List<GridExport> days) {
		if (days.isEmpty()) {
			return 0;
		}
		Map<String, GridExport> existing = repository.findAllById(days.stream().map(GridExport::id).toList())
				.stream()
				.collect(Collectors.toMap(GridExport::id, Function.identity()));
		List<GridExport> changed = new ArrayList<>();
		for (GridExport day : days) {
			GridExport stored = existing.get(day.id());
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
