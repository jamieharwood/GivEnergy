package com.example.givenergy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

@Configuration(proxyBeanMethods = false)
class MongoConfig {

	/**
	 * Use the driver's java.time codecs, which store a {@code LocalDate} as UTC midnight. Spring Data's default
	 * converts via the JVM time zone, so on a Europe/London machine summer dates were stored as 23:00 the day before
	 * and would read back a day out under any other zone.
	 */
	@Bean
	MongoCustomConversions mongoCustomConversions() {
		return MongoCustomConversions.create(MongoCustomConversions.MongoConverterConfigurationAdapter::useNativeDriverJavaTimeCodecs);
	}

}
