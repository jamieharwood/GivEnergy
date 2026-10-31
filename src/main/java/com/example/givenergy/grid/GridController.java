package com.example.givenergy.grid;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

	public GridController(GridHistoryService service, GridHistorySync sync) {
		this.service = service;
		this.sync = sync;
	}

	/** Export totals and bars: {@code range} is {@code yesterday}, {@code week}, {@code month} or {@code year}. */
	@GetMapping("/exports")
	public GridHistoryService.GridView exports(@RequestParam(defaultValue = "month") String range) {
		return service.view(GridFlow.EXPORT, parse(range));
	}

	/** Import totals and bars, with the same ranges as {@link #exports}. */
	@GetMapping("/imports")
	public GridHistoryService.GridView imports(@RequestParam(defaultValue = "month") String range) {
		return service.view(GridFlow.IMPORT, parse(range));
	}

	/**
	 * Runs the import/export sync now (normally it runs at startup and just after midnight); returns how many
	 * documents were written. With {@code from}, re-fetches every day from that date, e.g. after changing the
	 * tariff's off-peak window.
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

}
