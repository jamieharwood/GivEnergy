package com.example.givenergy;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = { "givenergy.api-token=test-token", "givenergy.poll-interval=0",
		"givenergy.grid-history.sync-on-startup=false" })
class GivEnergyApplicationTests {

	@Test
	void contextLoads() {
	}

}
