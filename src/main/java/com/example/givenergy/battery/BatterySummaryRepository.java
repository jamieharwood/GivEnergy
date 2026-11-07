package com.example.givenergy.battery;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface BatterySummaryRepository extends MongoRepository<BatterySummary, String> {

	/** Summaries for one inverter whose date falls in {@code [from, to]}, oldest first. */
	@Query(value = "{ 'inverterSerial': ?0, 'date': { $gte: ?1, $lte: ?2 } }", sort = "{ 'date': 1 }")
	List<BatterySummary> findByInverterSerialAndDateInRange(String inverterSerial, LocalDate from, LocalDate to);

}
