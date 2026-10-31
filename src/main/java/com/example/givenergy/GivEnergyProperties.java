package com.example.givenergy;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings for the GivEnergy cloud API.
 *
 * @param baseUrl        API root, normally {@code https://api.givenergy.cloud/v1}
 * @param apiToken       personal API token created at givenergy.cloud (Account Settings → Manage API Tokens)
 * @param inverterSerial inverter serial number; if blank, the first inverter on the account is used
 * @param pollInterval   how often the background poller logs the battery status; {@code 0} disables polling
 * @param timezone       local time zone that defines a day for the daily summary (and its midnight cron trigger)
 */
@Validated
@ConfigurationProperties("givenergy")
public record GivEnergyProperties(
		@NotNull @DefaultValue("https://api.givenergy.cloud/v1") URI baseUrl,
		@NotBlank(message = "is required: set the GIVENERGY_API_TOKEN environment variable or givenergy.api-token in config/application.properties") String apiToken,
		String inverterSerial,
		@NotNull @DefaultValue("5m") Duration pollInterval,
		@NotNull @DefaultValue("Europe/London") ZoneId timezone) {
}
