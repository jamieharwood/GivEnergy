package com.example.givenergy;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Thresholds for the live alerts. {@code lowSocPercent} of 0 turns the low-charge alert off.
 */
@Validated
@ConfigurationProperties("givenergy.alerts")
public record AlertProperties(
		@NotNull @PositiveOrZero @Max(100) @DefaultValue("20") Integer lowSocPercent) {
}
