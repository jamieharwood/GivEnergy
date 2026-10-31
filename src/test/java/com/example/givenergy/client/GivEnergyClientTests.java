package com.example.givenergy.client;

import java.net.URI;
import java.time.Duration;

import com.example.givenergy.GivEnergyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class GivEnergyClientTests {

	private MockRestServiceServer server;

	private GivEnergyClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new GivEnergyClient(builder, new GivEnergyProperties(URI.create("https://api.test/v1"),
				"secret", "CE1234", Duration.ZERO));
	}

	@Test
	void readsLatestSystemData() {
		server.expect(requestTo("https://api.test/v1/inverter/CE1234/system-data/latest"))
				.andExpect(header("Authorization", "Bearer secret"))
				.andRespond(withSuccess("""
						{"data":{"time":"2026-10-31T12:00:00Z","status":"Normal",
						 "solar":{"power":1200,"arrays":[]},"grid":{"voltage":240.1,"power":-50},
						 "battery":{"percent":76,"power":-850,"temperature":21.5},
						 "inverter":{"temperature":30},"consumption":400}}
						""", MediaType.APPLICATION_JSON));

		SystemData data = client.latestSystemData("CE1234");

		assertThat(data.battery().percent()).isEqualTo(76);
		assertThat(data.battery().power()).isEqualTo(-850);
		assertThat(data.solar().power()).isEqualTo(1200);
		server.verify();
	}

	@Test
	void readsLatestMeterData() {
		server.expect(requestTo("https://api.test/v1/inverter/CE1234/meter-data/latest"))
				.andRespond(withSuccess("""
						{"data":{"time":"2026-10-31T12:00:00Z",
						 "today":{"solar":5.1,"grid":{"import":1.2,"export":0.3},"battery":{"charge":3.4,"discharge":1.1},"consumption":4.0},
						 "total":{"solar":900.0,"grid":{"import":1000.0,"export":200.0},"battery":{"charge":700.0,"discharge":650.0},"consumption":1500.0}}}
						""", MediaType.APPLICATION_JSON));

		MeterData data = client.latestMeterData("CE1234");

		assertThat(data.today().battery().charge()).isEqualTo(3.4);
		assertThat(data.today().battery().discharge()).isEqualTo(1.1);
	}

	@Test
	void unauthorizedBecomesAuthenticationException() {
		server.expect(requestTo("https://api.test/v1/inverter/CE1234/system-data/latest"))
				.andRespond(withUnauthorizedRequest().body("{\"message\":\"Unauthenticated.\"}")
						.contentType(MediaType.APPLICATION_JSON));

		assertThatExceptionOfType(GivEnergyAuthenticationException.class)
				.isThrownBy(() -> client.latestSystemData("CE1234"))
				.withMessageContaining("rejected the API token (HTTP 401)");
	}

	@Test
	void forbiddenBecomesAuthenticationException() {
		server.expect(requestTo("https://api.test/v1/inverter/CE1234/meter-data/latest"))
				.andRespond(withStatus(HttpStatus.FORBIDDEN));

		assertThatExceptionOfType(GivEnergyAuthenticationException.class)
				.isThrownBy(() -> client.latestMeterData("CE1234"))
				.withMessageContaining("api:inverter:read");
	}

}
