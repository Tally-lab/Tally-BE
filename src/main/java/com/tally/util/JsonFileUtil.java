package com.tally.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class JsonFileUtil {
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final String DATA_DIR = "data";

    static {
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
        } catch (IOException e) {
            log.error("Failed to create data directory", e);
        }
    }

    public static <T> void writeToFile(String fileName, T data) {
        try {
            Path filePath = Paths.get(DATA_DIR, fileName);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), data);
            log.debug("Saved data to file: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to write to file: {}", fileName, e);
            throw new RuntimeException("Failed to write to file: " + fileName, e);
        }
    }

    public static <T> T readFromFile(String fileName, Class<T> clazz) {
        try {
            Path filePath = Paths.get(DATA_DIR, fileName);
            File file = filePath.toFile();

            if (!file.exists()) {
                return null;
            }

            return objectMapper.readValue(file, clazz);
        } catch (IOException e) {
            log.error("Failed to read from file: {}", fileName, e);
            return null;
        }
    }


    public static <T> List<T> readFromFile(String fileName, TypeReference<List<T>> typeReference) {
        try {
            Path filePath = Paths.get(DATA_DIR, fileName);
            File file = filePath.toFile();

            if (!file.exists()) {
                return new ArrayList<>();
            }

            return objectMapper.readValue(file, typeReference);
        } catch (IOException e) {
            log.error("Failed to read from file: {}", fileName, e);
            return new ArrayList<>();
        }
    }

    public static void deleteFile(String fileName) {
        try {
            Path filePath = Paths.get(DATA_DIR, fileName);
            Files.deleteIfExists(filePath);
            log.debug("Deleted file: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", fileName, e);
        }
    }

    public static boolean fileExists(String fileName) {
        return Files.exists(Paths.get(DATA_DIR, fileName));
    }
}