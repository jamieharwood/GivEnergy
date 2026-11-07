package com.example.givenergy;

import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TariffPropertiesTests {

	private final TariffProperties tariff = new TariffProperties(16.5, 36.48, 6.6, LocalTime.of(23, 30),
			LocalTime.of(5, 30), 0.0);

	@Test
	void offPeakWindowWrapsMidnight() {
		assertThat(tariff.isOffPeak(LocalTime.of(23, 0))).isFalse();
		assertThat(tariff.isOffPeak(LocalTime.of(23, 30))).isTrue();
		assertThat(tariff.isOffPeak(LocalTime.of(0, 0))).isTrue();
		assertThat(tariff.isOffPeak(LocalTime.of(5, 0))).isTrue();
		assertThat(tariff.isOffPeak(LocalTime.of(5, 30))).isFalse();
		assertThat(tariff.isOffPeak(LocalTime.of(12, 0))).isFalse();
	}

	@Test
	void offPeakWindowWithinOneDay() {
		TariffProperties daytime = new TariffProperties(15.0, 30.0, 10.0, LocalTime.of(1, 0), LocalTime.of(4, 0), 0.0);

		assertThat(daytime.isOffPeak(LocalTime.of(0, 30))).isFalse();
		assertThat(daytime.isOffPeak(LocalTime.of(1, 0))).isTrue();
		assertThat(daytime.isOffPeak(LocalTime.of(3, 30))).isTrue();
		assertThat(daytime.isOffPeak(LocalTime.of(4, 0))).isFalse();
	}

	@Test
	void justAfterOffPeakIsTheFollowingFourHours() {
		assertThat(tariff.isJustAfterOffPeak(LocalTime.of(5, 0))).isFalse();
		assertThat(tariff.isJustAfterOffPeak(LocalTime.of(5, 30))).isTrue();
		assertThat(tariff.isJustAfterOffPeak(LocalTime.of(9, 0))).isTrue();
		assertThat(tariff.isJustAfterOffPeak(LocalTime.of(9, 30))).isFalse();
		assertThat(tariff.isJustAfterOffPeak(LocalTime.of(15, 0))).isFalse();
	}

}
