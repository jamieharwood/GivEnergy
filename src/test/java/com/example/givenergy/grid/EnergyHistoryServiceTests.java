package com.example.givenergy.grid;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import com.example.givenergy.GivEnergyProperties;
import com.example.givenergy.TariffProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class EnergyHistoryServiceTests {

	private static final ZoneId LONDON = ZoneId.of("Europe/London");

	private final EnergyBalanceRepository balances = mock(EnergyBalanceRepository.class);

	private final GridHistoryService grid = new GridHistoryService(mock(GridExportRepository.class),
			mock(GridImportRepository.class),
			new GivEnergyProperties(URI.create("https://api.test/v1"), "token", null, Duration.ZERO, LONDON),
			new TariffProperties(16.5, 36.48, 6.6, LocalTime.of(23, 30), LocalTime.of(5, 30), 0.0),
			Clock.fixed(Instant.parse("2026-10-31T14:00:00Z"), LONDON));

	private final EnergyHistoryService service = new EnergyHistoryService(balances, grid);

	@Test
	void solarAndHouseViewsDeriveTheRatios() {
		given(balances.findByInverterSerialAndDateInRange("CE1", LocalDate.of(2026, 10, 30),
				LocalDate.of(2026, 10, 30))).willReturn(List.of(new EnergyBalance("CE1_2026-10-30", "CE1",
						LocalDate.of(2026, 10, 30), 4, 1, 2, 3, 0, 5, 0, Instant.EPOCH)));

		GridHistoryService.GridView solar = service.view("CE1", GridFlow.SOLAR, GridRange.YESTERDAY, null, false);
		GridHistoryService.GridView house = service.view("CE1", GridFlow.HOUSE, GridRange.YESTERDAY, null, false);

		assertThat(solar.totalKwh()).isEqualTo(7);
		assertThat(solar.kwh()).containsEntry("solarToHome", 4.0).containsEntry("solarToGrid", 2.0);
		assertThat(solar.selfConsumptionRatio()).isEqualTo(0.714);
		assertThat(solar.selfSufficiencyRatio()).isNull();
		assertThat(house.totalKwh()).isEqualTo(12);
		assertThat(house.selfSufficiencyRatio()).isEqualTo(0.583);
		assertThat(house.amountGbp()).isZero();
	}

}
