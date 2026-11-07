package com.example.givenergy.grid;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface HalfHourFlowRepository extends MongoRepository<HalfHourFlow, String> {

	@Query(value = "{ 'inverterSerial': ?0, 'start': { $gte: ?1, $lt: ?2 } }", sort = "{ 'start': 1 }")
	List<HalfHourFlow> findByInverterSerialAndStartInRange(String inverterSerial, LocalDateTime from, LocalDateTime to);

	void deleteByInverterSerialAndStartBefore(String inverterSerial, LocalDateTime cutoff);

}
