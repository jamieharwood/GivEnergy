package com.example.givenergy.grid;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.givenergy.GivEnergyProperties;
import com.example.givenergy.TariffProperties;
import com.example.givenergy.client.CommunicationDevice;
import com.example.givenergy.client.EnergyFlowPeriod;
import com.example.givenergy.client.GivEnergyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.data.mongodb.repository.MongoRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GridHistorySyncTests {

	private static final ZoneId LONDON = ZoneId.of("Europe/London");

	// 00:30 GMT on 31 Oct 2026, so "today" is the 31st and yesterday the 30th
	private static final Instant NOW = Instant.parse("2026-10-31T00:30:00Z");

	static final TariffProperties TARIFF = new TariffProperties(16.5, 36.48, 6.6, LocalTime.of(23, 30),
			LocalTime.of(5, 30));

	private final GivEnergyClient client = mock(GivEnergyClient.class);

	private final GridExportRepository exports = mock(GridExportRepository.class);

	private final GridImportRepository imports = mock(GridImportRepository.class);

	private final GridHistorySync sync = new GridHistorySync(client, exports, imports,
			new GivEnergyProperties(URI.create("https://api.test/v1"), "token", null, Duration.ZERO, LONDON), TARIFF,
			Clock.fixed(NOW, LONDON), false);

	@BeforeEach
	void inverter() {
		given(client.communicationDevices()).willReturn(List.of(new CommunicationDevice("WO1",
				new CommunicationDevice.Inverter("CE1", "GIV-AC3.0", OffsetDateTime.parse("2026-10-20T00:00:00Z")))));
		given(exports.findAllById(anyIterable())).willReturn(List.of());
		given(imports.findAllById(anyIterable())).willReturn(List.of());
	}

	@Test
	void sumsHalfHoursIntoDaysSplitsOffPeakAndDropsPaddedEdgeDays() {
		given(exports.findTopByInverterSerialOrderByDateDesc("CE1")).willReturn(Optional.empty());
		given(imports.findTopByInverterSerialOrderByDateDesc("CE1")).willReturn(Optional.empty());
		// The request is padded by a day each side; the API's edge buckets must be ignored.
		given(client.halfHourlyEnergyFlows(eq("CE1"), eq(LocalDate.of(2026, 10, 19)), eq(LocalDate.of(2026, 10, 21)), any()))
				.willReturn(List.of());
		given(client.halfHourlyEnergyFlows(eq("CE1"), eq(LocalDate.of(2026, 10, 29)), eq(LocalDate.of(2026, 11, 1)), any()))
				.willReturn(List.of(
						flow("2026-10-29 23:30", 0, 0, 9, 9),
						flow("2026-10-30 00:00", 0, 0, 0.2, 1.3), // off-peak
						flow("2026-10-30 05:00", 0, 0, 0.5, 0), // off-peak (window ends 05:30)
						flow("2026-10-30 05:30", 0, 0, 1.0, 0), // peak
						flow("2026-10-30 12:00", 5.0299999999999985, 0.16, 0, 0),
						flow("2026-10-30 23:30", 0, 0, 1.0000000000000009, 0.9), // off-peak again
						flow("2026-10-31 00:00", 99, 99, 99, 99)));

		sync.sync("CE1", LocalDate.of(2026, 10, 30), LocalDate.of(2026, 10, 31));

		assertThat(saved(exports)).extracting(GridExport::id, GridExport::solarToGridKwh, GridExport::exportKwh)
				.containsExactly(tuple("CE1_2026-10-30", 5.03, 5.19));
		assertThat(saved(imports)).extracting(GridImport::id, GridImport::importKwh, GridImport::offPeakImportKwh)
				.containsExactly(tuple("CE1_2026-10-30", 4.9, 3.9));
	}

	@Test
	void laterRunsRecheckRecentDaysAndOnlyWriteChanges() {
		given(exports.findTopByInverterSerialOrderByDateDesc("CE1")).willReturn(Optional.of(export("2026-10-29", 0.3)));
		given(imports.findTopByInverterSerialOrderByDateDesc("CE1")).willReturn(Optional.of(importOf("2026-10-29", 30.8, 10)));
		// Re-checks the 28th and 29th, fetches the new 30th.
		given(client.halfHourlyEnergyFlows(eq("CE1"), eq(LocalDate.of(2026, 10, 27)), eq(LocalDate.of(2026, 11, 1)), any()))
				.willReturn(List.of(flow("2026-10-28 12:00", 15.5, 0, 24.8, 0), flow("2026-10-29 01:00", 0, 0, 10, 0),
						flow("2026-10-29 12:00", 0.3, 0, 20.8, 0), flow("2026-10-30 12:00", 13.2, 0, 3.7, 0)));
		given(exports.findAllById(anyIterable()))
				.willReturn(List.of(export("2026-10-28", 15.4), export("2026-10-29", 0.3)));
		// The 28th's import is unchanged in total but was stored without its off-peak split.
		given(imports.findAllById(anyIterable()))
				.willReturn(List.of(importOf("2026-10-28", 24.8, 5), importOf("2026-10-29", 30.8, 10)));

		int written = sync.sync();

		assertThat(written).isEqualTo(4);
		assertThat(saved(exports)).extracting(GridExport::id).containsExactly("CE1_2026-10-28", "CE1_2026-10-30");
		assertThat(saved(imports)).extracting(GridImport::id).containsExactly("CE1_2026-10-28", "CE1_2026-10-30");
	}

	@Test
	void anEmptyImportCollectionTriggersAFullBackfill() {
		given(exports.findTopByInverterSerialOrderByDateDesc("CE1")).willReturn(Optional.of(export("2026-10-29", 0.3)));
		given(imports.findTopByInverterSerialOrderByDateDesc("CE1")).willReturn(Optional.empty());
		given(client.halfHourlyEnergyFlows(eq("CE1"), any(), any(), any())).willReturn(List.of());

		sync.sync();

		verify(client).halfHourlyEnergyFlows(eq("CE1"), eq(LocalDate.of(2026, 10, 19)), eq(LocalDate.of(2026, 11, 1)), any());
	}

	@Test
	void importsStoredWithoutTheRateSplitTriggerAFullBackfill() {
		given(exports.findTopByInverterSerialOrderByDateDesc("CE1")).willReturn(Optional.of(export("2026-10-29", 0.3)));
		given(imports.findTopByInverterSerialOrderByDateDesc("CE1")).willReturn(Optional.of(
				new GridImport("CE1_2026-10-29", "CE1", LocalDate.of(2026, 10, 29), 30.8, 0, 30.8, null, Instant.EPOCH)));
		given(client.halfHourlyEnergyFlows(eq("CE1"), any(), any(), any())).willReturn(List.of());

		sync.sync();

		verify(client).halfHourlyEnergyFlows(eq("CE1"), eq(LocalDate.of(2026, 10, 19)), eq(LocalDate.of(2026, 11, 1)), any());
	}

	@Test
	void longRangesAreFetchedInChunksWithinTheBucketLimit() {
		LocalDate from = LocalDate.of(2026, 1, 1);
		given(client.halfHourlyEnergyFlows(eq("CE1"), any(), any(), any())).willReturn(List.of());

		sync.sync("CE1", from, from.plusDays(GridHistorySync.CHUNK_DAYS * 2 + 1));

		verify(client).halfHourlyEnergyFlows(eq("CE1"), eq(from.minusDays(1)),
				eq(from.plusDays(GridHistorySync.CHUNK_DAYS + 1)), any());
		verify(client).halfHourlyEnergyFlows(eq("CE1"), eq(from.plusDays(GridHistorySync.CHUNK_DAYS * 2L).minusDays(1)),
				eq(from.plusDays(GridHistorySync.CHUNK_DAYS * 2L + 2)), any());
		// Padded chunk, with 50 half-hours on the autumn clock-change day.
		assertThat((GridHistorySync.CHUNK_DAYS + 2) * 48 + 2).isLessThanOrEqualTo(GivEnergyClient.MAX_ENERGY_FLOW_BUCKETS);
	}

	@SuppressWarnings("unchecked")
	private static <T> List<T> saved(MongoRepository<T, String> repository) {
		ArgumentCaptor<List<T>> captor = ArgumentCaptor.forClass(List.class);
		verify(repository).saveAll(captor.capture());
		return captor.getValue();
	}

	private static EnergyFlowPeriod flow(String start, double solarToGrid, double batteryToGrid, double gridToHome,
			double gridToBattery) {
		return new EnergyFlowPeriod(LocalDateTime.parse(start.replace(' ', 'T')),
				Map.of(2, solarToGrid, 6, batteryToGrid, 3, gridToHome, 4, gridToBattery));
	}

	private static GridExport export(String date, double solar) {
		return GridExport.of("CE1", LocalDate.parse(date), solar, 0, Instant.EPOCH);
	}

	private static GridImport importOf(String date, double home, double offPeak) {
		return GridImport.of("CE1", LocalDate.parse(date), home, 0, offPeak, Instant.EPOCH);
	}

}
