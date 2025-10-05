package com.tally.repository;

import com.tally.domain.AnalysisResult;
import java.util.List;
import java.util.Optional;

public interface AnalysisResultRepository {
    AnalysisResult save(AnalysisResult result);
    Optional<AnalysisResult> findById(String id);
    List<AnalysisResult> findByUserId(String userId);
    Optional<AnalysisResult> findByCsvDataId(String csvDataId);
    void delete(String id);
}