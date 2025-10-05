package com.tally.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CSVData {
    private String id;
    private String userId;
    private String fileName;
    private List<String> headers;
    private List<Map<String, String>> rows;
    private LocalDateTime uploadedAt;
    private int totalRows;
}