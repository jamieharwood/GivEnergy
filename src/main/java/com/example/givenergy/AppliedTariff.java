package com.example.givenergy;

/**
 * The prices in force for one stored day, in pence. Copied from {@link TariffProperties} when the day is first
 * written and kept across later re-fetches, so a tariff change does not revalue history.
 */
public record AppliedTariff(
		double exportPence,
		double importPeakPence,
		double importOffPeakPence,
		double standingChargePence) {

	public static AppliedTariff from(TariffProperties tariff) {
		return new AppliedTariff(tariff.exportPence(), tariff.importPeakPence(), tariff.importOffPeakPence(),
				tariff.standingChargePence());
	}

}
