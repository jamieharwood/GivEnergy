package com.example.givenergy.battery;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/battery")
public class BatteryController {

	private final BatteryService batteryService;

	private final BatterySummaryJob summaryJob;

	public BatteryController(BatteryService batteryService, BatterySummaryJob summaryJob) {
		this.batteryService = batteryService;
		this.summaryJob = summaryJob;
	}

	@GetMapping
	public BatteryStatus status() {
		return batteryService.currentStatus();
	}

	/** (Re)builds the summary for a day, e.g. to back-fill a day the midnight job missed. */
	@PostMapping("/summaries/{date}")
	public List<BatterySummary> summarise(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return summaryJob.summarise(date);
	}

}
