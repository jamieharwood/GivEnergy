package com.example.givenergy.grid;

import java.util.ArrayList;
import java.util.List;

import com.example.givenergy.battery.Candle;
import com.example.givenergy.battery.CandleService;

/** CSV renderings of the chart payloads. */
public final class ViewsCsv {

	private ViewsCsv() {
	}

	public static String grid(GridHistoryService.GridView view) {
		List<String> header = new ArrayList<>();
		header.add(view.bucket());
		header.add("end");
		header.addAll(view.flow().parts());
		header.add("totalKwh");
		boolean money = view.flow() == GridFlow.IMPORT || view.flow() == GridFlow.EXPORT;
		if (view.flow() == GridFlow.IMPORT) {
			header.add("offPeakKwh");
			header.add("peakKwh");
			header.add("offPeakGbp");
			header.add("peakGbp");
		}
		if (money) {
			header.add("amountGbp");
		}
		List<String> lines = new ArrayList<>();
		lines.add(line(header.toArray()));
		for (GridHistoryService.Bar bar : view.bars()) {
			List<Object> cells = new ArrayList<>();
			cells.add(bar.start());
			cells.add(bar.end());
			for (String part : view.flow().parts()) {
				cells.add(bar.days() == 0 ? "" : bar.kwh().get(part));
			}
			cells.add(bar.days() == 0 ? "" : bar.totalKwh());
			if (view.flow() == GridFlow.IMPORT) {
				cells.add(bar.days() == 0 ? "" : bar.kwhByRate().get("offPeak"));
				cells.add(bar.days() == 0 ? "" : bar.kwhByRate().get("peak"));
				cells.add(bar.days() == 0 ? "" : bar.amountGbpByRate().get("offPeak"));
				cells.add(bar.days() == 0 ? "" : bar.amountGbpByRate().get("peak"));
			}
			if (money) {
				cells.add(bar.days() == 0 ? "" : bar.amountGbp());
			}
			lines.add(line(cells.toArray()));
		}
		return String.join("\n", lines) + "\n";
	}

	public static String candles(CandleService.CandleChart chart) {
		List<String> lines = new ArrayList<>();
		lines.add(line("start", "end", "open", "high", "low", "close", "lowAt", "highAt", "chargedKwh",
				"dischargedKwh", "maxChargePowerWatts", "maxDischargePowerWatts", "readings"));
		for (Candle candle : chart.candles()) {
			lines.add(line(candle.start(), candle.end(), candle.open(), candle.high(), candle.low(), candle.close(),
					candle.lowAt(), candle.highAt(), candle.chargedKwh(), candle.dischargedKwh(),
					candle.maxChargePowerWatts(), candle.maxDischargePowerWatts(), candle.readingCount()));
		}
		return String.join("\n", lines) + "\n";
	}

	private static String line(Object... cells) {
		StringBuilder row = new StringBuilder();
		for (int i = 0; i < cells.length; i++) {
			if (i > 0) {
				row.append(',');
			}
			row.append(cell(cells[i]));
		}
		return row.toString();
	}

	private static String cell(Object value) {
		if (value == null) {
			return "";
		}
		String text = String.valueOf(value);
		if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0) {
			return '"' + text.replace("\"", "\"\"") + '"';
		}
		return text;
	}

}
