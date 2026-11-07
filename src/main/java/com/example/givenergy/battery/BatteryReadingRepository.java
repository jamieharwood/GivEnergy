package com.example.givenergy.battery;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface BatteryReadingRepository extends MongoRepository<BatteryReading, String> {

	/** Readings for one inverter whose cloud time falls in {@code [from, to)}. */
	@Query("{ 'inverterSerial': ?0, 'time': { $gte: ?1, $lt: ?2 } }")
	List<BatteryReading> findByInverterSerialAndTimeInRange(String inverterSerial, Instant from, Instant to);

	/** Readings whose cloud time falls in {@code [from, to)}, across every inverter. */
	@Query("{ 'time': { $gte: ?0, $lt: ?1 } }")
	List<BatteryReading> findByTimeInRange(Instant from, Instant to);

}
