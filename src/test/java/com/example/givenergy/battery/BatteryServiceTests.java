package com.example.givenergy.battery;

import java.time.OffsetDateTime;

import com.example.givenergy.client.MeterData;
import com.example.givenergy.client.SystemData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BatteryServiceTests {

	@Test
	void negativePowerIsCharging() {
		BatteryStatus status = BatteryService.toStatus("CE1234", system(-850), meter());

		assertThat(status.state()).isEqualTo(BatteryState.CHARGING);
		assertThat(status.chargePowerWatts()).isEqualTo(850);
		assertThat(status.dischargePowerWatts()).isZero();
		assertThat(status.stateOfChargePercent()).isEqualTo(60);
		assertThat(status.chargedTodayKwh()).isEqualTo(3.4);
	}

	@Test
	void positivePowerIsDischarging() {
		BatteryStatus status = BatteryService.toStatus("CE1234", system(1200), meter());

		assertThat(status.state()).isEqualTo(BatteryState.DISCHARGING);
		assertThat(status.dischargePowerWatts()).isEqualTo(1200);
		assertThat(status.chargePowerWatts()).isZero();
	}

	@Test
	void zeroPowerIsIdle() {
		assertThat(BatteryService.toStatus("CE1234", system(0), meter()).state()).isEqualTo(BatteryState.IDLE);
	}

	private static SystemData system(int batteryPower) {
		return new SystemData(OffsetDateTime.now(), "Normal", new SystemData.Battery(60, batteryPower, 20.0), null,
				null, null);
	}

	private static MeterData meter() {
		return new MeterData(OffsetDateTime.now(), new MeterData.Totals(new MeterData.Battery(3.4, 1.1)), null);
	}

}
