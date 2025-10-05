package com.tally.repository;

import com.tally.domain.CSVData;
import java.util.List;
import java.util.Optional;

public interface CSVDataRepository {
    CSVData save(CSVData csvData);
    Optional<CSVData> findById(String id);
    List<CSVData> findByUserId(String userId);
    void delete(String id);
}