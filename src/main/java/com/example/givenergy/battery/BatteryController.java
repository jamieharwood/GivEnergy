package com.example.givenergy.battery;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/battery")
public class BatteryController {

	private final BatteryService batteryService;

	private final BatterySummaryJob summaryJob;

	private final CandleService candleService;

	public BatteryController(BatteryService batteryService, BatterySummaryJob summaryJob,
			CandleService candleService) {
		this.batteryService = batteryService;
		this.summaryJob = summaryJob;
		this.candleService = candleService;
	}

	@GetMapping
	public BatteryStatus status() {
		return batteryService.currentStatus();
	}

	/** State-of-charge candles for the chart: {@code range} is {@code day}, {@code week} or {@code year}. */
	@GetMapping("/candles")
	public CandleService.CandleChart candles(@RequestParam(defaultValue = "week") String range) {
		CandleRange candleRange;
		try {
			candleRange = CandleRange.from(range);
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "range must be day, week or year");
		}
		return candleService.candles(candleRange);
	}

	/** (Re)builds the summary for a day, e.g. to back-fill a day the midnight job missed. */
	@PostMapping("/summaries/{date}")
	public List<BatterySummary> summarise(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return summaryJob.summarise(date);
	}

}
