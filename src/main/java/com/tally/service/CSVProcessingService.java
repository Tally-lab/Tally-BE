package com.tally.service;

import com.tally.domain.CSVData;
import com.tally.repository.CSVDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CSVProcessingService {

    private final CSVDataRepository csvDataRepository;

    public CSVData processCSV(String userId, MultipartFile file) throws IOException {
        log.info("Processing CSV file: {} for user: {}", file.getOriginalFilename(), userId);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {

            // 헤더 추출
            List<String> headers = new ArrayList<>(csvParser.getHeaderMap().keySet());
            log.debug("CSV headers: {}", headers);

            // 데이터 파싱 (스트림 처리)
            List<Map<String, String>> rows = new ArrayList<>();
            int rowCount = 0;

            for (CSVRecord record : csvParser) {
                Map<String, String> row = new HashMap<>();
                for (String header : headers) {
                    row.put(header, record.get(header));
                }
                rows.add(row);
                rowCount++;

                // 배치 처리 (1000행씩)
                if (rowCount % 1000 == 0) {
                    log.debug("Processed {} rows", rowCount);
                }
            }

            // CSVData 객체 생성 및 저장
            CSVData csvData = CSVData.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .fileName(file.getOriginalFilename())
                    .headers(headers)
                    .rows(rows)
                    .totalRows(rowCount)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            csvDataRepository.save(csvData);
            log.info("CSV processing completed: {} rows", rowCount);

            return csvData;
        }
    }

    public CSVData getCSVData(String csvDataId) {
        return csvDataRepository.findById(csvDataId)
                .orElseThrow(() -> new RuntimeException("CSV data not found: " + csvDataId));
    }

    public List<String> detectColumnTypes(CSVData csvData) {
        // 컬럼 타입 자동 감지 (날짜, 시간, 숫자, 문자열)
        List<String> columnTypes = new ArrayList<>();

        for (String header : csvData.getHeaders()) {
            String type = detectType(csvData.getRows(), header);
            columnTypes.add(type);
            log.debug("Column '{}' detected as type: {}", header, type);
        }

        return columnTypes;
    }

    private String detectType(List<Map<String, String>> rows, String columnName) {
        if (rows.isEmpty()) {
            return "STRING";
        }

        // 첫 5행 샘플링
        int sampleSize = Math.min(5, rows.size());
        List<String> samples = rows.stream()
                .limit(sampleSize)
                .map(row -> row.get(columnName))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (samples.isEmpty()) {
            return "STRING";
        }

        // 숫자 타입 체크
        boolean allNumeric = samples.stream().allMatch(this::isNumeric);
        if (allNumeric) {
            return "NUMBER";
        }

        // 날짜 타입 체크 (간단한 패턴)
        boolean allDate = samples.stream().allMatch(this::isDate);
        if (allDate) {
            return "DATE";
        }

        // 시간 타입 체크
        boolean allTime = samples.stream().allMatch(this::isTime);
        if (allTime) {
            return "TIME";
        }

        return "STRING";
    }

    private boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isDate(String str) {
        // 간단한 날짜 패턴 체크 (YYYY-MM-DD, YYYY/MM/DD 등)
        return str.matches("\\d{4}[-/]\\d{2}[-/]\\d{2}");
    }

    private boolean isTime(String str) {
        // 간단한 시간 패턴 체크 (HH:mm, HH:mm:ss 등)
        return str.matches("\\d{2}:\\d{2}(:\\d{2})?");
    }
}