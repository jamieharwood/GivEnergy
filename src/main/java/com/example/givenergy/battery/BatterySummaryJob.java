package com.example.givenergy.battery;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.example.givenergy.GivEnergyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Builds a daily min/max summary of the recorded battery readings and stores it in {@code batterySummaries}. */
@Component
public class BatterySummaryJob {

	private static final Logger log = LoggerFactory.getLogger(BatterySummaryJob.class);

	private final BatteryReadingRepository readings;

	private final BatterySummaryRepository summaries;

	private final ZoneId zone;

	private final Clock clock;

	BatterySummaryJob(BatteryReadingRepository readings, BatterySummaryRepository summaries,
			GivEnergyProperties properties, Clock clock) {
		this.readings = readings;
		this.summaries = summaries;
		this.zone = properties.timezone();
		this.clock = clock;
	}

	/** At local midnight, summarise the day that has just ended. */
	@Scheduled(cron = "0 0 0 * * *", zone = "${givenergy.timezone:Europe/London}")
	public void summariseYesterday() {
		try {
			summarise(LocalDate.now(clock.withZone(zone)).minusDays(1));
		}
		catch (RuntimeException ex) {
			log.warn("Failed to create daily battery summary: {}", ex.getMessage());
		}
	}

	public List<BatterySummary> summarise(LocalDate date) {
		Instant from = date.atStartOfDay(zone).toInstant();
		Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
		Map<String, List<BatteryReading>> bySerial = readings.findByTimeInRange(from, to)
				.stream()
				.collect(Collectors.groupingBy(BatteryReading::inverterSerial, TreeMap::new, Collectors.toList()));
		if (bySerial.isEmpty()) {
			log.info("No battery readings recorded for {}; no summary created", date);
			return List.of();
		}
		Instant now = Instant.now(clock);
		List<BatterySummary> created = bySerial.entrySet().stream()
				.map(entry -> summarise(entry.getKey(), date, entry.getValue(), now))
				.toList();
		summaries.saveAll(created);
		created.forEach(s -> log.info("Battery summary {} {}: SoC {}%-{}%, charged {} kWh, discharged {} kWh ({} readings)",
				s.inverterSerial(), s.date(), s.minStateOfChargePercent(), s.maxStateOfChargePercent(), s.chargedKwh(),
				s.dischargedKwh(), s.readingCount()));
		return created;
	}

	private BatterySummary summarise(String serial, LocalDate date, List<BatteryReading> dayReadings, Instant now) {
		Comparator<BatteryReading> bySoc = Comparator.comparingInt(BatteryReading::stateOfChargePercent);
		Comparator<BatteryReading> byTime = Comparator.comparing(BatteryReading::time);
		// Earliest reading wins ties, so "min at"/"max at" is when the level was first reached.
		BatteryReading min = dayReadings.stream().min(bySoc.thenComparing(byTime)).orElseThrow();
		BatteryReading max = dayReadings.stream().max(bySoc.thenComparing(byTime.reversed())).orElseThrow();
		BatteryReading first = dayReadings.stream().min(byTime).orElseThrow();
		BatteryReading last = dayReadings.stream().max(byTime).orElseThrow();
		return new BatterySummary(serial + "_" + date, serial, date, zone.getId(), dayReadings.size(),
				first.stateOfChargePercent(), last.stateOfChargePercent(),
				min.stateOfChargePercent(), min.time(), max.stateOfChargePercent(), max.time(),
				dayReadings.stream().mapToInt(BatteryReading::chargePowerWatts).max().orElse(0),
				dayReadings.stream().mapToInt(BatteryReading::dischargePowerWatts).max().orElse(0),
				last.chargedTodayKwh(), last.dischargedTodayKwh(), now);
	}

}
