package com.example.givenergy;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = { "givenergy.api-token=test-token", "givenergy.poll-interval=0" })
class GivEnergyApplicationTests {

	@Test
	void contextLoads() {
	}

}
