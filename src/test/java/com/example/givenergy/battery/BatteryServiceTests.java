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
		return new MeterData(OffsetDateTime.now(),
				new MeterData.Totals(null, null, new MeterData.Battery(3.4, 1.1), null), null);
	}

	@Test
	void livePowerAndMeterCountersAreKept() {
		OffsetDateTime time = OffsetDateTime.parse("2026-10-31T12:00:00Z");
		SystemData system = new SystemData(time, "Normal", new SystemData.Battery(76, -850, 21.5),
				new SystemData.Solar(1200), new SystemData.Grid(-50), 400);
		MeterData meter = new MeterData(time,
				new MeterData.Totals(5.1, new MeterData.Grid(1.2, 0.3), new MeterData.Battery(3.4, 1.1), 4.0),
				new MeterData.Totals(900.0, new MeterData.Grid(1000.0, 200.0), new MeterData.Battery(700.0, 650.0),
						1500.0));

		BatteryStatus status = BatteryService.toStatus("CE1234", system, meter);

		assertThat(status.inverterStatus()).isEqualTo("Normal");
		assertThat(status.power()).isEqualTo(new BatteryStatus.PowerNow(1200, 0, 50, 400));
		assertThat(status.today().solarKwh()).isEqualTo(5.1);
		assertThat(status.today().gridImportKwh()).isEqualTo(1.2);
		assertThat(status.lifetime().gridExportKwh()).isEqualTo(200.0);
		assertThat(status.lifetime().consumptionKwh()).isEqualTo(1500.0);
	}

}
