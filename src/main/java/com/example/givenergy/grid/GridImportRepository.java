package com.example.givenergy.grid;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface GridImportRepository extends MongoRepository<GridImport, String> {

	Optional<GridImport> findTopByInverterSerialOrderByDateDesc(String inverterSerial);

	/** Imports whose date falls in {@code [from, to]}, oldest first. */
	@Query(value = "{ 'date': { $gte: ?0, $lte: ?1 } }", sort = "{ 'date': 1 }")
	List<GridImport> findByDateInRange(LocalDate from, LocalDate to);

}
