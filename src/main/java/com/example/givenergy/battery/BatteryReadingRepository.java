package com.example.givenergy.battery;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface BatteryReadingRepository extends MongoRepository<BatteryReading, String> {

	/** Readings whose cloud time falls in {@code [from, to)}. */
	@Query("{ 'time': { $gte: ?0, $lt: ?1 } }")
	List<BatteryReading> findByTimeInRange(Instant from, Instant to);

}
