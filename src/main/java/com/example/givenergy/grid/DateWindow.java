package com.example.givenergy.grid;

import java.time.LocalDate;

/**
 * Inclusive local-date window. {@code monthly} is true when the year view groups days into calendar months.
 */
record DateWindow(LocalDate from, LocalDate to, boolean monthly) {

	DateWindow yearEarlier() {
		return new DateWindow(from.minusYears(1), to.minusYears(1), monthly);
	}

}
