package com.example.givenergy.client;

import java.util.List;

import com.example.givenergy.GivEnergyProperties;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Thin wrapper over the GivEnergy cloud REST API. Every response is wrapped in a {@code {"data": ...}} envelope.
 */
@Component
public class GivEnergyClient {

	private final RestClient restClient;

	public GivEnergyClient(RestClient.Builder builder, GivEnergyProperties properties) {
		this.restClient = builder
				.baseUrl(properties.baseUrl().toString())
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
				.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
				.defaultStatusHandler(status -> status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN,
						(request, response) -> {
							throw new GivEnergyAuthenticationException(response.getStatusCode());
						})
				.build();
	}

	public List<CommunicationDevice> communicationDevices() {
		return restClient.get()
				.uri("/communication-device")
				.retrieve()
				.body(new ParameterizedTypeReference<DataEnvelope<List<CommunicationDevice>>>() {
				})
				.data();
	}

	public SystemData latestSystemData(String inverterSerial) {
		return restClient.get()
				.uri("/inverter/{serial}/system-data/latest", inverterSerial)
				.retrieve()
				.body(new ParameterizedTypeReference<DataEnvelope<SystemData>>() {
				})
				.data();
	}

	public MeterData latestMeterData(String inverterSerial) {
		return restClient.get()
				.uri("/inverter/{serial}/meter-data/latest", inverterSerial)
				.retrieve()
				.body(new ParameterizedTypeReference<DataEnvelope<MeterData>>() {
				})
				.data();
	}

}
