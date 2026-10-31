package com.example.givenergy.grid;

import java.util.Locale;

/**
 * Views offered by the grid charts, all ending yesterday: a single day, the last 7 or 30 days (daily bars), or the
 * last 12 calendar months (monthly bars, the current month to date).
 */
public enum GridRange {

	YESTERDAY, WEEK, MONTH, YEAR;

	static GridRange from(String value) {
		return valueOf(value.trim().toUpperCase(Locale.ROOT));
	}

}
