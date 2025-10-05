package com.tally.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    public static <T> void writeToFile(String fileName, T data) throws IOException {
        Path filePath = Paths.get(DATA_DIR, fileName);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), data);
        log.debug("Saved data to file: {}", filePath);
    }

    public static <T> T readFromFile(String fileName, Class<T> clazz) throws IOException {
        Path filePath = Paths.get(DATA_DIR, fileName);
        File file = filePath.toFile();

        if (!file.exists()) {
            return null;
        }

        return objectMapper.readValue(file, clazz);
    }

    public static void deleteFile(String fileName) throws IOException {
        Path filePath = Paths.get(DATA_DIR, fileName);
        Files.deleteIfExists(filePath);
        log.debug("Deleted file: {}", filePath);
    }

    public static boolean fileExists(String fileName) {
        return Files.exists(Paths.get(DATA_DIR, fileName));
    }
}