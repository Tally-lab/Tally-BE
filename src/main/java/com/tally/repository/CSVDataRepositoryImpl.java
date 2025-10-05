package com.tally.repository;

import com.tally.domain.CSVData;
import com.tally.util.JsonFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class CSVDataRepositoryImpl implements CSVDataRepository {

    private static final String FILE_PREFIX = "csvdata_";
    private static final String FILE_SUFFIX = ".json";

    @Override
    public CSVData save(CSVData csvData) {
        try {
            String fileName = FILE_PREFIX + csvData.getId() + FILE_SUFFIX;
            JsonFileUtil.writeToFile(fileName, csvData);
            log.info("CSVData saved: {}", csvData.getId());
            return csvData;
        } catch (IOException e) {
            log.error("Failed to save CSV data", e);
            throw new RuntimeException("Failed to save CSV data", e);
        }
    }

    @Override
    public Optional<CSVData> findById(String id) {
        try {
            String fileName = FILE_PREFIX + id + FILE_SUFFIX;
            CSVData csvData = JsonFileUtil.readFromFile(fileName, CSVData.class);
            return Optional.ofNullable(csvData);
        } catch (IOException e) {
            log.error("Failed to find CSV data by id: {}", id, e);
            return Optional.empty();
        }
    }

    @Override
    public List<CSVData> findByUserId(String userId) {
        // 간단한 구현: 모든 파일을 스캔하지 않고 빈 리스트 반환
        // 실제로는 인덱스 파일을 만들거나 파일명에 userId를 포함시켜야 함
        log.warn("findByUserId is not fully implemented");
        return new ArrayList<>();
    }

    @Override
    public void delete(String id) {
        try {
            String fileName = FILE_PREFIX + id + FILE_SUFFIX;
            JsonFileUtil.deleteFile(fileName);
            log.info("CSVData deleted: {}", id);
        } catch (IOException e) {
            log.error("Failed to delete CSV data", e);
            throw new RuntimeException("Failed to delete CSV data", e);
        }
    }
}