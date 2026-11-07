package com.example.givenergy.grid;

import java.time.LocalDate;

import com.example.givenergy.InverterDirectory;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/energy")
public class EnergyController {

	private final EnergyHistoryService service;

	private final InverterDirectory inverters;

	public EnergyController(EnergyHistoryService service, InverterDirectory inverters) {
		this.service = service;
		this.inverters = inverters;
	}

	/** Solar generation split into home, battery and grid. Ranges match the grid charts. */
	@GetMapping("/solar")
	public GridHistoryService.GridView solar(@RequestParam(defaultValue = "month") String range,
			@RequestParam(required = false) String inverter,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor,
			@RequestParam(defaultValue = "false") boolean compare) {
		return view(GridFlow.SOLAR, range, inverter, anchor, compare);
	}

	@GetMapping(value = "/solar.csv", produces = "text/csv")
	public ResponseEntity<String> solarCsv(@RequestParam(defaultValue = "month") String range,
			@RequestParam(required = false) String inverter,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor,
			@RequestParam(defaultValue = "false") boolean compare) {
		return csv("solar.csv", ViewsCsv.grid(solar(range, inverter, anchor, compare)));
	}

	/** House consumption split into solar, battery and grid. */
	@GetMapping("/house")
	public GridHistoryService.GridView house(@RequestParam(defaultValue = "month") String range,
			@RequestParam(required = false) String inverter,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor,
			@RequestParam(defaultValue = "false") boolean compare) {
		return view(GridFlow.HOUSE, range, inverter, anchor, compare);
	}

	@GetMapping(value = "/house.csv", produces = "text/csv")
	public ResponseEntity<String> houseCsv(@RequestParam(defaultValue = "month") String range,
			@RequestParam(required = false) String inverter,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor,
			@RequestParam(defaultValue = "false") boolean compare) {
		return csv("house.csv", ViewsCsv.grid(house(range, inverter, anchor, compare)));
	}

	private GridHistoryService.GridView view(GridFlow flow, String range, String inverter, LocalDate anchor,
			boolean compare) {
		GridRange parsed;
		try {
			parsed = GridRange.from(range);
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "range must be yesterday, week, month or year");
		}
		return service.view(inverters.resolve(inverter), flow, parsed, anchor, compare);
	}

	private static ResponseEntity<String> csv(String filename, String body) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
				.body(body);
	}

}
