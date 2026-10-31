package com.example.givenergy.battery;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface BatterySummaryRepository extends MongoRepository<BatterySummary, String> {
}
