package com.example.givenergy;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

	private final AlertService alerts;

	public AlertController(AlertService alerts) {
		this.alerts = alerts;
	}

	@GetMapping
	public List<AlertService.Alert> current() {
		return alerts.current();
	}

}
