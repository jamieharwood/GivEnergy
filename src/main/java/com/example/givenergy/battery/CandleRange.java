package com.example.givenergy.battery;

import java.util.Locale;

/** Views offered by the chart: the last day as hourly candles, or the last week/year as daily candles. */
public enum CandleRange {

	DAY, WEEK, YEAR;

	static CandleRange from(String value) {
		return valueOf(value.trim().toUpperCase(Locale.ROOT));
	}

}
