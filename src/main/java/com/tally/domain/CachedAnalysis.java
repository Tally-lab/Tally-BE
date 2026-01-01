package com.tally.domain;

import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.Map;

/**
 * DynamoDB 캐시용 분석 결과 엔티티
 */
@Data
@DynamoDbBean
public class CachedAnalysis {

    // Partition Key: "owner/repo" or "org:orgname"
    private String id;

    // Sort Key: "contribution:username" or "quality:all"
    private String analysisType;

    // 분석 결과 데이터 (JSON 문자열)
    private String data;

    // 마지막 업데이트 시간
    private String lastUpdatedAt;

    // TTL (Unix timestamp in seconds)
    private Long ttl;

    // 버전 (스키마 변경 추적)
    private String version;

    // 메타데이터 (검색/필터링용)
    private Integer commitCount;
    private Integer prCount;
    private Integer issueCount;
    private String username; // GSI용

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("analysisType")
    public String getAnalysisType() {
        return analysisType;
    }

    @DynamoDbAttribute("data")
    public String getData() {
        return data;
    }

    @DynamoDbAttribute("lastUpdatedAt")
    public String getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    @DynamoDbAttribute("ttl")
    public Long getTtl() {
        return ttl;
    }

    @DynamoDbAttribute("version")
    public String getVersion() {
        return version;
    }

    @DynamoDbAttribute("commitCount")
    public Integer getCommitCount() {
        return commitCount;
    }

    @DynamoDbAttribute("prCount")
    public Integer getPrCount() {
        return prCount;
    }

    @DynamoDbAttribute("issueCount")
    public Integer getIssueCount() {
        return issueCount;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "username-index")
    @DynamoDbAttribute("username")
    public String getUsername() {
        return username;
    }

    /**
     * TTL 계산 (현재 시간 + duration)
     */
    public static Long calculateTTL(long durationSeconds) {
        return Instant.now().getEpochSecond() + durationSeconds;
    }

    /**
     * 캐시 만료 여부 확인
     */
    public boolean isExpired() {
        if (ttl == null) {
            return false;
        }
        return Instant.now().getEpochSecond() > ttl;
    }
}
