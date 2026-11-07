package com.example.givenergy.grid;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface GridImportRepository extends MongoRepository<GridImport, String> {

	Optional<GridImport> findTopByInverterSerialOrderByDateDesc(String inverterSerial);

	/** Imports for one inverter whose date falls in {@code [from, to]}, oldest first. */
	@Query(value = "{ 'inverterSerial': ?0, 'date': { $gte: ?1, $lte: ?2 } }", sort = "{ 'date': 1 }")
	List<GridImport> findByInverterSerialAndDateInRange(String inverterSerial, LocalDate from, LocalDate to);

	List<GridImport> findByAppliedTariffIsNull();

}
