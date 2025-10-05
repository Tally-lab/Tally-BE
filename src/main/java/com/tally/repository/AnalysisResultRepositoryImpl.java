package com.tally.repository;

import com.tally.domain.AnalysisResult;
import com.tally.util.JsonFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class AnalysisResultRepositoryImpl implements AnalysisResultRepository {

    private static final String FILE_PREFIX = "analysis_";
    private static final String FILE_SUFFIX = ".json";

    @Override
    public AnalysisResult save(AnalysisResult result) {
        try {
            String fileName = FILE_PREFIX + result.getId() + FILE_SUFFIX;
            JsonFileUtil.writeToFile(fileName, result);
            log.info("AnalysisResult saved: {}", result.getId());
            return result;
        } catch (IOException e) {
            log.error("Failed to save analysis result", e);
            throw new RuntimeException("Failed to save analysis result", e);
        }
    }

    @Override
    public Optional<AnalysisResult> findById(String id) {
        try {
            String fileName = FILE_PREFIX + id + FILE_SUFFIX;
            AnalysisResult result = JsonFileUtil.readFromFile(fileName, AnalysisResult.class);
            return Optional.ofNullable(result);
        } catch (IOException e) {
            log.error("Failed to find analysis result by id: {}", id, e);
            return Optional.empty();
        }
    }

    @Override
    public List<AnalysisResult> findByUserId(String userId) {
        log.warn("findByUserId is not fully implemented");
        return new ArrayList<>();
    }

    @Override
    public Optional<AnalysisResult> findByCsvDataId(String csvDataId) {
        try {
            String fileName = FILE_PREFIX + csvDataId + FILE_SUFFIX;
            AnalysisResult result = JsonFileUtil.readFromFile(fileName, AnalysisResult.class);
            return Optional.ofNullable(result);
        } catch (IOException e) {
            log.error("Failed to find analysis result by csvDataId: {}", csvDataId, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String id) {
        try {
            String fileName = FILE_PREFIX + id + FILE_SUFFIX;
            JsonFileUtil.deleteFile(fileName);
            log.info("AnalysisResult deleted: {}", id);
        } catch (IOException e) {
            log.error("Failed to delete analysis result", e);
            throw new RuntimeException("Failed to delete analysis result", e);
        }
    }
}