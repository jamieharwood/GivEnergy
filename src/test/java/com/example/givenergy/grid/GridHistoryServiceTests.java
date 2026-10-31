package com.example.givenergy.grid;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.example.givenergy.GivEnergyProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GridHistoryServiceTests {

	private static final ZoneId LONDON = ZoneId.of("Europe/London");

	private final GridExportRepository exports = mock(GridExportRepository.class);

	private final GridImportRepository imports = mock(GridImportRepository.class);

	private final GridHistoryService service = new GridHistoryService(exports, imports,
			new GivEnergyProperties(URI.create("https://api.test/v1"), "token", null, Duration.ZERO, LONDON),
			GridHistorySyncTests.TARIFF, Clock.fixed(Instant.parse("2026-10-31T14:00:00Z"), LONDON));

	@Test
	void weekHasABarForEveryDayEvenWithoutData() {
		given(exports.findByDateInRange(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 30)))
				.willReturn(List.of(export("2026-10-28", 15.5, 0), export("2026-10-30", 13.2, 0.5)));

		GridHistoryService.GridView view = service.view(GridFlow.EXPORT, GridRange.WEEK);

		assertThat(view.bucket()).isEqualTo("day");
		assertThat(view.bars()).hasSize(7);
		assertThat(view.bars().getFirst().days()).isZero();
		assertThat(view.bars().getFirst().kwh()).containsEntry("solarToGrid", 0.0);
		assertThat(view.bars().getLast()).isEqualTo(new GridHistoryService.Bar(LocalDate.of(2026, 10, 30),
				LocalDate.of(2026, 10, 30), Map.of("solarToGrid", 13.2, "batteryToGrid", 0.5), 13.7, Map.of(), 2.26, Map.of(), 1));
		assertThat(view.totalKwh()).isEqualTo(29.2);
		// 29.2 kWh at 16.5p
		assertThat(view.amountGbp()).isEqualTo(4.82);
		assertThat(view.kwh()).containsExactly(Map.entry("solarToGrid", 28.7), Map.entry("batteryToGrid", 0.5));
		assertThat(view.days()).isEqualTo(2);
	}

	@Test
	void importViewUsesImportPartsAndCostsEachRate() {
		given(imports.findByDateInRange(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 30)))
				.willReturn(List.of(GridImport.of("CE1", LocalDate.of(2026, 10, 30), 3.7, 5.8, 7.5, Instant.EPOCH)));

		GridHistoryService.GridView view = service.view(GridFlow.IMPORT, GridRange.YESTERDAY);

		assertThat(view.flow()).isEqualTo(GridFlow.IMPORT);
		assertThat(view.kwh()).containsExactly(Map.entry("gridToHome", 3.7), Map.entry("gridToBattery", 5.8));
		assertThat(view.totalKwh()).isEqualTo(9.5);
		assertThat(view.kwhByRate()).containsExactly(Map.entry("offPeak", 7.5), Map.entry("peak", 2.0));
		// 7.5 kWh at 6.6p + 2.0 kWh at 36.48p = 49.5p + 72.96p, each rounded to the penny
		assertThat(view.amountGbpByRate()).containsExactly(Map.entry("offPeak", 0.5), Map.entry("peak", 0.73));
		assertThat(view.amountGbp()).isEqualTo(1.23);
	}

	@Test
	void yearIsTwelveMonthlyBarsEndingYesterday() {
		given(exports.findByDateInRange(LocalDate.of(2025, 11, 1), LocalDate.of(2026, 10, 30)))
				.willReturn(List.of(export("2025-11-03", 1, 0), export("2025-11-20", 2, 0.25),
						export("2026-10-30", 4, 0)));

		List<GridHistoryService.Bar> bars = service.view(GridFlow.EXPORT, GridRange.YEAR).bars();

		assertThat(bars).hasSize(12);
		assertThat(bars.getFirst()).isEqualTo(new GridHistoryService.Bar(LocalDate.of(2025, 11, 1),
				LocalDate.of(2025, 11, 30), Map.of("solarToGrid", 3.0, "batteryToGrid", 0.25), 3.25, Map.of(), 0.54, Map.of(), 2));
		assertThat(bars.getLast().end()).isEqualTo(LocalDate.of(2026, 10, 30));
	}

	private static GridExport export(String date, double solar, double battery) {
		return GridExport.of("CE1", LocalDate.parse(date), solar, battery, Instant.EPOCH);
	}

}
