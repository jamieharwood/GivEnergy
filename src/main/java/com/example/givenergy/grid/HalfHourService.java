package com.example.givenergy.grid;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.givenergy.GivEnergyProperties;
import com.example.givenergy.TariffProperties;

import org.springframework.stereotype.Service;

/** Recent half-hourly flows, including today, for the time-of-day chart. */
@Service
public class HalfHourService {

	private final HalfHourFlowRepository flows;

	private final TariffProperties tariff;

	private final ZoneId zone;

	private final Clock clock;

	HalfHourService(HalfHourFlowRepository flows, TariffProperties tariff, GivEnergyProperties properties,
			Clock clock) {
		this.flows = flows;
		this.tariff = tariff;
		this.zone = properties.timezone();
		this.clock = clock;
	}

	public HalfHourChart chart(String serial, int days) {
		int span = Math.clamp(days, 1, GridHistorySync.HALF_HOUR_DAYS);
		LocalDate today = LocalDate.now(clock.withZone(zone));
		LocalDate from = today.minusDays(span - 1);
		LocalDateTime start = from.atStartOfDay();
		LocalDateTime end = today.plusDays(1).atStartOfDay();
		List<Bucket> buckets = flows.findByInverterSerialAndStartInRange(serial, start, end).stream()
				.map(Bucket::of)
				.toList();
		return new HalfHourChart(from, today, tariff.offPeakStart().toString(), tariff.offPeakEnd().toString(),
				buckets);
	}

	public record HalfHourChart(LocalDate from, LocalDate to, String offPeakStart, String offPeakEnd,
			List<Bucket> buckets) {
	}

	public record Bucket(LocalDateTime start, boolean offPeak, Map<String, Double> kwh, double importKwh,
			double exportKwh) {

		static Bucket of(HalfHourFlow flow) {
			Map<String, Double> kwh = new LinkedHashMap<>();
			kwh.put("solarToHome", flow.solarToHomeKwh());
			kwh.put("solarToBattery", flow.solarToBatteryKwh());
			kwh.put("solarToGrid", flow.solarToGridKwh());
			kwh.put("batteryToHome", flow.batteryToHomeKwh());
			kwh.put("batteryToGrid", flow.batteryToGridKwh());
			kwh.put("gridToHome", flow.gridToHomeKwh());
			kwh.put("gridToBattery", flow.gridToBatteryKwh());
			return new Bucket(flow.start(), flow.offPeak(), kwh, flow.importKwh(), flow.exportKwh());
		}

	}

}
