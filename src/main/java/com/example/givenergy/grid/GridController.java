package com.example.givenergy.grid;

import java.time.LocalDate;

import com.example.givenergy.InverterDirectory;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/grid")
public class GridController {

	private final GridHistoryService service;

	private final GridHistorySync sync;

	private final HalfHourService halfHours;

	private final InverterDirectory inverters;

	public GridController(GridHistoryService service, GridHistorySync sync, HalfHourService halfHours,
			InverterDirectory inverters) {
		this.service = service;
		this.sync = sync;
		this.halfHours = halfHours;
		this.inverters = inverters;
	}

	/** Export totals and bars: {@code range} is {@code yesterday}, {@code week}, {@code month} or {@code year}. */
	@GetMapping("/exports")
	public GridHistoryService.GridView exports(@RequestParam(defaultValue = "month") String range,
			@RequestParam(required = false) String inverter,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor,
			@RequestParam(defaultValue = "false") boolean compare) {
		return service.view(inverters.resolve(inverter), GridFlow.EXPORT, parse(range), anchor, compare);
	}

	@GetMapping(value = "/exports.csv", produces = "text/csv")
	public ResponseEntity<String> exportsCsv(@RequestParam(defaultValue = "month") String range,
			@RequestParam(required = false) String inverter,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor,
			@RequestParam(defaultValue = "false") boolean compare) {
		return csv("grid-exports.csv", ViewsCsv.grid(exports(range, inverter, anchor, compare)));
	}

	/** Import totals and bars, with the same ranges as {@link #exports}. */
	@GetMapping("/imports")
	public GridHistoryService.GridView imports(@RequestParam(defaultValue = "month") String range,
			@RequestParam(required = false) String inverter,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor,
			@RequestParam(defaultValue = "false") boolean compare) {
		return service.view(inverters.resolve(inverter), GridFlow.IMPORT, parse(range), anchor, compare);
	}

	@GetMapping(value = "/imports.csv", produces = "text/csv")
	public ResponseEntity<String> importsCsv(@RequestParam(defaultValue = "month") String range,
			@RequestParam(required = false) String inverter,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor,
			@RequestParam(defaultValue = "false") boolean compare) {
		return csv("grid-imports.csv", ViewsCsv.grid(imports(range, inverter, anchor, compare)));
	}

	/** Import cost, export earnings, standing charge and the net of the three, for the same window. */
	@GetMapping("/bill")
	public GridHistoryService.Bill bill(@RequestParam(defaultValue = "month") String range,
			@RequestParam(required = false) String inverter,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor,
			@RequestParam(defaultValue = "false") boolean compare) {
		return service.bill(inverters.resolve(inverter), parse(range), anchor, compare);
	}

	/** Half-hourly import and export for the last {@code days} days, including today. {@code days} is 1 to 7. */
	@GetMapping("/half-hours")
	public HalfHourService.HalfHourChart halfHours(@RequestParam(defaultValue = "2") int days,
			@RequestParam(required = false) String inverter) {
		return halfHours.chart(inverters.resolve(inverter), days);
	}

	/**
	 * Runs the import/export sync now (normally it runs at startup and just after midnight); returns how many
	 * documents were written. With {@code from}, re-fetches every day from that date, e.g. after changing the
	 * tariff's off-peak window. Stored prices are left as they were.
	 */
	@PostMapping("/sync")
	public int sync(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
		return from == null ? sync.sync() : sync.syncFrom(from);
	}

	private static GridRange parse(String range) {
		try {
			return GridRange.from(range);
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "range must be yesterday, week, month or year");
		}
	}

	private static ResponseEntity<String> csv(String filename, String body) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
				.body(body);
	}

}
