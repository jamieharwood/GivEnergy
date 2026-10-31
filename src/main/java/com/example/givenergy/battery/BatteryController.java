package com.example.givenergy.battery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/battery")
public class BatteryController {

	private final BatteryService batteryService;

	public BatteryController(BatteryService batteryService) {
		this.batteryService = batteryService;
	}

	@GetMapping
	public BatteryStatus status() {
		return batteryService.currentStatus();
	}

}
