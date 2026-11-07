package com.example.givenergy.grid;

import java.time.LocalDate;

/**
 * Turns a chart range into a date window. With no anchor the presets end yesterday (the last complete day): a single
 * day, the last 7 or 30 days, or the last 12 calendar months. An anchor picks that day, the 7 days ending on it, the
 * calendar month containing it, or the 12 months ending with its month.
 */
final class RangeWindows {

	private RangeWindows() {
	}

	static DateWindow of(GridRange range, LocalDate yesterday, LocalDate anchor) {
		if (anchor == null) {
			return switch (range) {
				case YESTERDAY -> new DateWindow(yesterday, yesterday, false);
				case WEEK -> new DateWindow(yesterday.minusDays(6), yesterday, false);
				case MONTH -> new DateWindow(yesterday.minusDays(29), yesterday, false);
				case YEAR -> new DateWindow(yesterday.withDayOfMonth(1).minusMonths(11), yesterday, true);
			};
		}
		LocalDate end = anchor.isAfter(yesterday) ? yesterday : anchor;
		return switch (range) {
			case YESTERDAY -> new DateWindow(end, end, false);
			case WEEK -> new DateWindow(end.minusDays(6), end, false);
			case MONTH -> {
				LocalDate start = end.withDayOfMonth(1);
				LocalDate monthEnd = start.plusMonths(1).minusDays(1);
				yield new DateWindow(start, monthEnd.isAfter(yesterday) ? yesterday : monthEnd, false);
			}
			case YEAR -> new DateWindow(end.withDayOfMonth(1).minusMonths(11), end, true);
		};
	}

}
