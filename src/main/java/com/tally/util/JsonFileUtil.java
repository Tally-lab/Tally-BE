package com.tally.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class JsonFileUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * 데이터를 JSON 파일로 저장 (단일 객체 또는 리스트)
     */
    public static <T> void writeToFile(String filePath, T data) {
        try {
            // 디렉토리가 없으면 생성
            Path path = Paths.get(filePath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            // JSON 파일로 저장
            objectMapper.writeValue(new File(filePath), data);
            log.debug("Saved data to file: {}", filePath);

        } catch (IOException e) {
            log.error("Failed to save data to file: {}", filePath, e);
            throw new RuntimeException("Failed to save data to file", e);
        }
    }

    /**
     * JSON 파일에서 단일 객체 로드
     */
    public static <T> T readFromFile(String filePath, Class<T> clazz) {
        try {
            File file = new File(filePath);

            if (!file.exists()) {
                log.warn("File does not exist: {}", filePath);
                return null;
            }

            return objectMapper.readValue(file, clazz);

        } catch (IOException e) {
            log.error("Failed to load data from file: {}", filePath, e);
            return null;
        }
    }

    /**
     * JSON 파일에서 데이터 로드 (TypeReference 사용)
     * 주로 List, Map 등 제네릭 타입에 사용
     */
    public static <T> T readFromFile(String filePath, TypeReference<T> typeReference) {
        try {
            File file = new File(filePath);

            if (!file.exists()) {
                log.warn("File does not exist: {}, returning empty list", filePath);
                // 파일이 없으면 빈 리스트 반환 (List<T>인 경우)
                return objectMapper.readValue("[]", typeReference);
            }

            return objectMapper.readValue(file, typeReference);

        } catch (IOException e) {
            log.error("Failed to load data from file: {}", filePath, e);
            try {
                // 에러 발생 시 빈 리스트 반환
                return objectMapper.readValue("[]", typeReference);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to load data from file", e);
            }
        }
    }

    /**
     * 데이터를 JSON 파일로 저장 (리스트)
     */
    public static <T> void saveToFile(String filePath, T data) {
        writeToFile(filePath, data);
    }

    /**
     * JSON 파일에서 리스트 데이터 로드
     */
    public static <T> List<T> loadFromFile(String filePath, Class<T[]> clazz) {
        try {
            File file = new File(filePath);

            if (!file.exists()) {
                log.warn("File does not exist: {}", filePath);
                return new ArrayList<>();
            }

            T[] array = objectMapper.readValue(file, clazz);
            return new ArrayList<>(Arrays.asList(array));

        } catch (IOException e) {
            log.error("Failed to load data from file: {}", filePath, e);
            return new ArrayList<>();
        }
    }

    /**
     * 단일 객체를 JSON 파일에서 로드
     */
    public static <T> T loadSingleFromFile(String filePath, Class<T> clazz) {
        return readFromFile(filePath, clazz);
    }

    /**
     * 파일 존재 여부 확인
     */
    public static boolean fileExists(String filePath) {
        return new File(filePath).exists();
    }

    /**
     * 파일 삭제
     */
    public static boolean deleteFile(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists()) {
                boolean deleted = file.delete();
                if (deleted) {
                    log.debug("Deleted file: {}", filePath);
                }
                return deleted;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to delete file: {}", filePath, e);
            return false;
        }
    }
}