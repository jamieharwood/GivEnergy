package com.example.givenergy.grid;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.givenergy.GivEnergyProperties;
import com.example.givenergy.client.CommunicationDevice;
import com.example.givenergy.client.EnergyFlowPeriod;
import com.example.givenergy.client.GivEnergyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GridExportSyncTests {

	private static final ZoneId LONDON = ZoneId.of("Europe/London");

	// 00:30 GMT on 31 Oct 2026, so "today" is the 31st and yesterday the 30th
	private static final Instant NOW = Instant.parse("2026-10-31T00:30:00Z");

	private final GivEnergyClient client = mock(GivEnergyClient.class);

	private final GridExportRepository repository = mock(GridExportRepository.class);

	private final GridExportSync sync = new GridExportSync(client, repository,
			new GivEnergyProperties(URI.create("https://api.test/v1"), "token", null, Duration.ZERO, LONDON),
			Clock.fixed(NOW, LONDON), false);

	@BeforeEach
	void inverter() {
		given(client.communicationDevices()).willReturn(List.of(new CommunicationDevice("WO1",
				new CommunicationDevice.Inverter("CE1", "GIV-AC3.0", OffsetDateTime.parse("2026-10-20T00:00:00Z")))));
		given(repository.findAllById(anyIterable())).willReturn(List.of());
	}

	@Test
	void firstRunBackfillsFromCommissioningAndDropsPaddedEdgeDays() {
		given(repository.findTopByInverterSerialOrderByDateDesc("CE1")).willReturn(Optional.empty());
		// The request is padded by a day each side; the API's edge buckets must be ignored.
		given(client.dailyEnergyFlows(eq("CE1"), eq(LocalDate.of(2026, 10, 19)), eq(LocalDate.of(2026, 11, 1)), any()))
				.willReturn(List.of(flow("2026-10-19", 99, 99), flow("2026-10-20", 5.0299999999999985, 0.16),
						flow("2026-10-30", 13.2, 0), flow("2026-10-31", 99, 99)));

		int written = sync.sync();

		assertThat(written).isEqualTo(2);
		assertThat(saved()).extracting(GridExport::id, GridExport::solarToGridKwh, GridExport::exportKwh)
				.containsExactly(tuple("CE1_2026-10-20", 5.03, 5.19),
						tuple("CE1_2026-10-30", 13.2, 13.2));
	}

	@Test
	void laterRunsRecheckRecentDaysAndOnlyWriteChanges() {
		given(repository.findTopByInverterSerialOrderByDateDesc("CE1"))
				.willReturn(Optional.of(export("2026-10-29", 0.3)));
		// Re-checks the 28th and 29th, fetches the new 30th.
		given(client.dailyEnergyFlows(eq("CE1"), eq(LocalDate.of(2026, 10, 27)), eq(LocalDate.of(2026, 11, 1)), any()))
				.willReturn(List.of(flow("2026-10-28", 15.5, 0), flow("2026-10-29", 0.3, 0),
						flow("2026-10-30", 13.2, 0)));
		given(repository.findAllById(anyIterable()))
				.willReturn(List.of(export("2026-10-28", 15.4), export("2026-10-29", 0.3)));

		int written = sync.sync();

		assertThat(written).isEqualTo(2);
		assertThat(saved()).extracting(GridExport::id).containsExactly("CE1_2026-10-28", "CE1_2026-10-30");
	}

	@Test
	void longRangesAreFetchedInChunks() {
		LocalDate from = LocalDate.of(2026, 1, 1);
		given(client.dailyEnergyFlows(eq("CE1"), any(), any(), any())).willReturn(List.of());

		sync.sync("CE1", from, from.plusDays(GridExportSync.CHUNK_DAYS * 2 + 1));

		verify(client).dailyEnergyFlows(eq("CE1"), eq(from.minusDays(1)),
				eq(from.plusDays(GridExportSync.CHUNK_DAYS + 1)), any());
		verify(client).dailyEnergyFlows(eq("CE1"), eq(from.plusDays(GridExportSync.CHUNK_DAYS * 2L).minusDays(1)),
				eq(from.plusDays(GridExportSync.CHUNK_DAYS * 2L + 2)), any());
	}

	@SuppressWarnings("unchecked")
	private List<GridExport> saved() {
		ArgumentCaptor<List<GridExport>> captor = ArgumentCaptor.forClass(List.class);
		verify(repository).saveAll(captor.capture());
		return captor.getValue();
	}

	private static EnergyFlowPeriod flow(String date, double solar, double battery) {
		return new EnergyFlowPeriod(LocalDate.parse(date), Map.of(2, solar, 6, battery));
	}

	private static GridExport export(String date, double solar) {
		return GridExport.of("CE1", LocalDate.parse(date), solar, 0, Instant.EPOCH);
	}

}
