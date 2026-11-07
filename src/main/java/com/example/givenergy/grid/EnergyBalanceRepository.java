package com.example.givenergy.grid;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface EnergyBalanceRepository extends MongoRepository<EnergyBalance, String> {

	Optional<EnergyBalance> findTopByInverterSerialOrderByDateDesc(String inverterSerial);

	@Query(value = "{ 'inverterSerial': ?0, 'date': { $gte: ?1, $lte: ?2 } }", sort = "{ 'date': 1 }")
	List<EnergyBalance> findByInverterSerialAndDateInRange(String inverterSerial, LocalDate from, LocalDate to);

}
