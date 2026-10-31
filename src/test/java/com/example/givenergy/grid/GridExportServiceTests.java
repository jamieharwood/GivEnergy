package com.example.givenergy.grid;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.example.givenergy.GivEnergyProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GridExportServiceTests {

	private static final ZoneId LONDON = ZoneId.of("Europe/London");

	private final GridExportRepository repository = mock(GridExportRepository.class);

	private final GridExportService service = new GridExportService(repository,
			new GivEnergyProperties(URI.create("https://api.test/v1"), "token", null, Duration.ZERO, LONDON),
			Clock.fixed(Instant.parse("2026-10-31T14:00:00Z"), LONDON));

	@Test
	void weekHasABarForEveryDayEvenWithoutData() {
		given(repository.findByDateInRange(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 30)))
				.willReturn(List.of(export("2026-10-28", 15.5, 0), export("2026-10-30", 13.2, 0.5)));

		GridExportService.GridExportView view = service.view(GridExportRange.WEEK);

		assertThat(view.bucket()).isEqualTo("day");
		assertThat(view.bars()).hasSize(7);
		assertThat(view.bars().getFirst().days()).isZero();
		assertThat(view.bars().getLast()).isEqualTo(new GridExportService.Bar(LocalDate.of(2026, 10, 30),
				LocalDate.of(2026, 10, 30), 13.2, 0.5, 13.7, 1));
		assertThat(view.exportKwh()).isEqualTo(29.2);
		assertThat(view.days()).isEqualTo(2);
	}

	@Test
	void yearIsTwelveMonthlyBarsEndingYesterday() {
		given(repository.findByDateInRange(LocalDate.of(2025, 11, 1), LocalDate.of(2026, 10, 30)))
				.willReturn(List.of(export("2025-11-03", 1, 0), export("2025-11-20", 2, 0.25),
						export("2026-10-30", 4, 0)));

		List<GridExportService.Bar> bars = service.view(GridExportRange.YEAR).bars();

		assertThat(bars).hasSize(12);
		assertThat(bars.getFirst()).isEqualTo(new GridExportService.Bar(LocalDate.of(2025, 11, 1),
				LocalDate.of(2025, 11, 30), 3, 0.25, 3.25, 2));
		assertThat(bars.getLast().end()).isEqualTo(LocalDate.of(2026, 10, 30));
	}

	@Test
	void yesterdayIsOneDay() {
		given(repository.findByDateInRange(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 30)))
				.willReturn(List.of(export("2026-10-30", 13.2, 0)));

		GridExportService.GridExportView view = service.view(GridExportRange.YESTERDAY);

		assertThat(view.bars()).singleElement().extracting(GridExportService.Bar::exportKwh).isEqualTo(13.2);
	}

	private static GridExport export(String date, double solar, double battery) {
		return GridExport.of("CE1", LocalDate.parse(date), solar, battery, Instant.EPOCH);
	}

}
