package com.example.givenergy.battery;

import com.example.givenergy.client.GivEnergyAuthenticationException;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BatteryController.class)
class BatteryControllerTests {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private BatteryService batteryService;

	@MockitoBean
	private BatterySummaryJob summaryJob;

	@MockitoBean
	private CandleService candleService;

	@Test
	void rejectedTokenReturnsBadGatewayWithExplanation() throws Exception {
		given(batteryService.currentStatus())
				.willThrow(new GivEnergyAuthenticationException(HttpStatus.UNAUTHORIZED));

		mvc.perform(get("/api/battery"))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.title").value("GivEnergy API token rejected"))
				.andExpect(jsonPath("$.givenergyStatus").value(401))
				.andExpect(jsonPath("$.detail").value(containsString("GIVENERGY_API_TOKEN")));
	}

}
