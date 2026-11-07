package com.example.givenergy;

import java.util.List;

import com.example.givenergy.client.CommunicationDevice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inverters")
public class InverterController {

	private final InverterDirectory directory;

	public InverterController(InverterDirectory directory) {
		this.directory = directory;
	}

	@GetMapping
	public List<InverterInfo> list() {
		return directory.list().stream().map(InverterInfo::of).toList();
	}

	public record InverterInfo(String serial, String model) {

		static InverterInfo of(CommunicationDevice.Inverter inverter) {
			return new InverterInfo(inverter.serial(), inverter.model());
		}

	}

}
