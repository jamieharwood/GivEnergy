package com.example.givenergy.grid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/grid/exports")
public class GridExportController {

	private final GridExportService service;

	private final GridExportSync sync;

	public GridExportController(GridExportService service, GridExportSync sync) {
		this.service = service;
		this.sync = sync;
	}

	/** Export totals and bars: {@code range} is {@code yesterday}, {@code week}, {@code month} or {@code year}. */
	@GetMapping
	public GridExportService.GridExportView exports(@RequestParam(defaultValue = "month") String range) {
		GridExportRange exportRange;
		try {
			exportRange = GridExportRange.from(range);
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "range must be yesterday, week, month or year");
		}
		return service.view(exportRange);
	}

	/** Runs the sync now (normally it runs at startup and just after midnight); returns how many days were written. */
	@PostMapping("/sync")
	public int sync() {
		return sync.sync();
	}

}
