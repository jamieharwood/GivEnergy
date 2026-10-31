package com.example.givenergy.battery;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface BatterySummaryRepository extends MongoRepository<BatterySummary, String> {

	/** Summaries whose date falls in {@code [from, to]}, oldest first. */
	@Query(value = "{ 'date': { $gte: ?0, $lte: ?1 } }", sort = "{ 'date': 1 }")
	List<BatterySummary> findByDateInRange(LocalDate from, LocalDate to);

}
