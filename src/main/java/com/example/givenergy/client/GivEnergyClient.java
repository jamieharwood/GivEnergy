package com.example.givenergy.client;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.givenergy.GivEnergyProperties;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Thin wrapper over the GivEnergy cloud REST API. Every response is wrapped in a {@code {"data": ...}} envelope.
 */
@Component
public class GivEnergyClient {

	/** energy-flows labels buckets like {@code 2026-10-30 23:30}. */
	private static final DateTimeFormatter BUCKET_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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

	/** Most buckets the energy-flows endpoint returns per request (it answers 422 above this). */
	public static final int MAX_ENERGY_FLOW_BUCKETS = 1000;

	/**
	 * Half-hourly energy flows for {@code [start, endExclusive)}, labelled in the account's local time. Half-hourly
	 * data is used rather than the daily grouping because the daily totals are wrong on some days around BST, and
	 * because time-of-use tariffs need the time of day. At most {@link #MAX_ENERGY_FLOW_BUCKETS} buckets (about 20
	 * days) per call.
	 */
	public List<EnergyFlowPeriod> halfHourlyEnergyFlows(String inverterSerial, LocalDate start, LocalDate endExclusive,
			Collection<Integer> types) {
		Map<String, Object> request = Map.of("start_time", start.toString(), "end_time", endExclusive.toString(),
				"grouping", 0, "types", types);
		JsonNode body = restClient.post()
				.uri("/inverter/{serial}/energy-flows", inverterSerial)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(JsonNode.class);
		List<EnergyFlowPeriod> periods = new ArrayList<>();
		JsonNode data = body == null ? null : body.get("data");
		if (data == null) {
			return periods;
		}
		// "data" is an object keyed "0", "1", ... (or an empty array when there is nothing).
		for (JsonNode bucket : data.values()) {
			Map<Integer, Double> kwh = new HashMap<>();
			for (Map.Entry<String, JsonNode> flow : bucket.get("data").properties()) {
				kwh.put(Integer.valueOf(flow.getKey()), flow.getValue().asDouble());
			}
			periods.add(new EnergyFlowPeriod(LocalDateTime.parse(bucket.get("start_time").asString(), BUCKET_TIME), kwh));
		}
		return periods;
	}

}
