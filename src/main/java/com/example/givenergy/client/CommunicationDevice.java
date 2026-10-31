package com.example.givenergy.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Entry from {@code GET /communication-device}; only the fields needed to discover the inverter. */
public record CommunicationDevice(
		@JsonProperty("serial_number") String serialNumber,
		Inverter inverter) {

	public record Inverter(String serial, String model) {
	}

}
