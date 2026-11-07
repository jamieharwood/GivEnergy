package com.example.givenergy.battery;

import java.time.LocalDate;
import java.util.List;

import com.example.givenergy.InverterDirectory;
import com.example.givenergy.grid.ViewsCsv;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

	private final InverterDirectory inverters;

	public BatteryController(BatteryService batteryService, BatterySummaryJob summaryJob, CandleService candleService,
			InverterDirectory inverters) {
		this.batteryService = batteryService;
		this.summaryJob = summaryJob;
		this.candleService = candleService;
		this.inverters = inverters;
	}

	@GetMapping
	public BatteryStatus status(@RequestParam(required = false) String inverter) {
		return batteryService.currentStatus(inverter);
	}

	/**
	 * State-of-charge candles. {@code range} is {@code day}, {@code week}, {@code month} or {@code year}.
	 * {@code anchor} picks the day the window ends on (a calendar month when the range is month).
	 * {@code compare} adds the same window a year earlier.
	 */
	@GetMapping("/candles")
	public CandleService.CandleChart candles(@RequestParam(defaultValue = "week") String range,
			@RequestParam(required = false) String inverter,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor,
			@RequestParam(defaultValue = "false") boolean compare) {
		return candleService.candles(inverters.resolve(inverter), parse(range), anchor, compare);
	}

	@GetMapping(value = "/candles.csv", produces = "text/csv")
	public ResponseEntity<String> candlesCsv(@RequestParam(defaultValue = "week") String range,
			@RequestParam(required = false) String inverter,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor,
			@RequestParam(defaultValue = "false") boolean compare) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"battery-candles.csv\"")
				.contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
				.body(ViewsCsv.candles(candles(range, inverter, anchor, compare)));
	}

	/** Power and temperature samples for {@code day} or {@code week}. */
	@GetMapping("/traces")
	public CandleService.TraceChart traces(@RequestParam(defaultValue = "day") String range,
			@RequestParam(required = false) String inverter) {
		try {
			return candleService.traces(inverters.resolve(inverter), parse(range));
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	/** (Re)builds the summary for a day, e.g. to back-fill a day the midnight job missed. */
	@PostMapping("/summaries/{date}")
	public List<BatterySummary> summarise(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return summaryJob.summarise(date);
	}

	private static CandleRange parse(String range) {
		try {
			return CandleRange.from(range);
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "range must be day, week, month or year");
		}
	}

}
