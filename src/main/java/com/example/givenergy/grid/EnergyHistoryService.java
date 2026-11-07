package com.example.givenergy.grid;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.example.givenergy.AppliedTariff;
import com.example.givenergy.TariffProperties;

import org.springframework.stereotype.Service;

/** Solar generation and house-supply charts, from the stored seven-way energy balance. */
@Service
public class EnergyHistoryService {

	private final EnergyBalanceRepository balances;

	private final GridHistoryService grid;

	EnergyHistoryService(EnergyBalanceRepository balances, GridHistoryService grid) {
		this.balances = balances;
		this.grid = grid;
	}

	public GridHistoryService.GridView view(String serial, GridFlow flow, GridRange range, LocalDate anchor,
			boolean compare) {
		if (flow != GridFlow.SOLAR && flow != GridFlow.HOUSE) {
			throw new IllegalArgumentException(flow + " is not an energy-balance view");
		}
		return grid.aggregate(flow, range, anchor, compare, window -> balances
				.findByInverterSerialAndDateInRange(serial, window.from(), window.to()).stream()
				.map(day -> (GridDay) new Slice(day.date(), day.parts(flow)))
				.toList());
	}

	private record Slice(LocalDate date, Map<String, Double> parts) implements GridDay {

		@Override
		public String id() {
			return date.toString();
		}

		@Override
		public AppliedTariff appliedTariff() {
			return null;
		}

		@Override
		public GridDay withAppliedTariff(AppliedTariff tariff) {
			return this;
		}

		@Override
		public double valuePence(TariffProperties tariff) {
			return 0;
		}

	}

}
