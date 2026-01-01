package com.tally.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tally.domain.CachedAnalysis;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * DynamoDB 기반 캐싱 서비스
 * 24시간 TTL로 분석 결과 캐싱
 */
@Slf4j
@Service
public class CacheService {

    private final DynamoDbTable<CachedAnalysis> table;
    private final ObjectMapper objectMapper;
    private final boolean cacheEnabled;
    private final long defaultTTLSeconds;

    // 캐시 통계
    private long cacheHits = 0;
    private long cacheMisses = 0;

    public CacheService() {
        this(true, Duration.ofHours(24).getSeconds());
    }

    public CacheService(boolean cacheEnabled, long defaultTTLSeconds) {
        this.cacheEnabled = cacheEnabled;
        this.defaultTTLSeconds = defaultTTLSeconds;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        if (cacheEnabled) {
            // DynamoDB 클라이언트 초기화
            String region = System.getenv().getOrDefault("DYNAMODB_REGION", "ap-northeast-2");
            DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                    .region(Region.of(region))
                    .build();

            DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                    .dynamoDbClient(dynamoDbClient)
                    .build();

            String tableName = System.getenv().getOrDefault("CACHE_TABLE_NAME", "tally-analysis-cache");
            this.table = enhancedClient.table(tableName, TableSchema.fromBean(CachedAnalysis.class));

            log.info("CacheService initialized with table: {}, TTL: {}h", tableName, defaultTTLSeconds / 3600);
        } else {
            this.table = null;
            log.info("CacheService disabled");
        }
    }

    /**
     * 캐시에서 데이터 조회
     */
    public <T> Optional<T> get(String repositoryId, String analysisType, Class<T> clazz) {
        if (!cacheEnabled) {
            return Optional.empty();
        }

        try {
            Key key = Key.builder()
                    .partitionValue(repositoryId)
                    .sortValue(analysisType)
                    .build();

            CachedAnalysis cached = table.getItem(key);

            if (cached == null) {
                cacheMisses++;
                log.debug("Cache miss: {}/{}", repositoryId, analysisType);
                return Optional.empty();
            }

            // TTL 체크
            if (cached.isExpired()) {
                cacheMisses++;
                log.debug("Cache expired: {}/{}", repositoryId, analysisType);
                // 만료된 항목 삭제 (DynamoDB TTL이 자동 삭제하지만 즉시 삭제)
                table.deleteItem(key);
                return Optional.empty();
            }

            // 데이터 역직렬화
            T data = objectMapper.readValue(cached.getData(), clazz);
            cacheHits++;
            log.debug("Cache hit: {}/{}", repositoryId, analysisType);
            return Optional.of(data);

        } catch (Exception e) {
            log.error("Failed to get from cache: {}/{}", repositoryId, analysisType, e);
            cacheMisses++;
            return Optional.empty();
        }
    }

    /**
     * 캐시에 데이터 저장
     */
    public <T> void put(String repositoryId, String analysisType, T data, Duration ttl) {
        if (!cacheEnabled) {
            return;
        }

        try {
            String dataJson = objectMapper.writeValueAsString(data);
            long ttlSeconds = ttl != null ? ttl.getSeconds() : defaultTTLSeconds;

            CachedAnalysis cached = new CachedAnalysis();
            cached.setId(repositoryId);
            cached.setAnalysisType(analysisType);
            cached.setData(dataJson);
            cached.setLastUpdatedAt(Instant.now().toString());
            cached.setTtl(CachedAnalysis.calculateTTL(ttlSeconds));
            cached.setVersion("v1");

            // 메타데이터 추출 (분석 타입에 따라)
            extractMetadata(cached, data);

            table.putItem(cached);
            log.debug("Cache put: {}/{}, TTL: {}s", repositoryId, analysisType, ttlSeconds);

        } catch (Exception e) {
            log.error("Failed to put to cache: {}/{}", repositoryId, analysisType, e);
        }
    }

    /**
     * 캐시 무효화 (특정 레포지토리의 모든 분석 결과 삭제)
     */
    public void invalidate(String repositoryId) {
        if (!cacheEnabled) {
            return;
        }

        try {
            // DynamoDB Query로 해당 repositoryId의 모든 항목 조회 후 삭제
            // 간단히 알려진 analysisType들만 삭제
            String[] analysisTypes = {"contribution", "quality", "commits", "prs", "issues"};

            for (String analysisType : analysisTypes) {
                Key key = Key.builder()
                        .partitionValue(repositoryId)
                        .sortValue(analysisType)
                        .build();

                table.deleteItem(key);
            }

            log.info("Cache invalidated for repository: {}", repositoryId);

        } catch (Exception e) {
            log.error("Failed to invalidate cache for: {}", repositoryId, e);
        }
    }

    /**
     * 캐시 통계 조회
     */
    public CacheStatistics getStatistics() {
        CacheStatistics stats = new CacheStatistics();
        stats.setCacheHits(cacheHits);
        stats.setCacheMisses(cacheMisses);
        stats.setTotalRequests(cacheHits + cacheMisses);

        if (stats.getTotalRequests() > 0) {
            stats.setHitRate((double) cacheHits / stats.getTotalRequests() * 100);
        }

        stats.setCacheEnabled(cacheEnabled);
        return stats;
    }

    /**
     * 캐시 통계 리셋
     */
    public void resetStatistics() {
        cacheHits = 0;
        cacheMisses = 0;
    }

    /**
     * 메타데이터 추출 (타입별 처리)
     */
    private <T> void extractMetadata(CachedAnalysis cached, T data) {
        // 간단히 commitCount, prCount 등을 추출
        // 실제로는 data의 타입에 따라 리플렉션이나 타입 체크 필요
        // 여기서는 생략
    }

    /**
     * 캐시 통계 DTO
     */
    public static class CacheStatistics {
        private long cacheHits;
        private long cacheMisses;
        private long totalRequests;
        private double hitRate;
        private boolean cacheEnabled;

        // Getters and Setters
        public long getCacheHits() { return cacheHits; }
        public void setCacheHits(long cacheHits) { this.cacheHits = cacheHits; }

        public long getCacheMisses() { return cacheMisses; }
        public void setCacheMisses(long cacheMisses) { this.cacheMisses = cacheMisses; }

        public long getTotalRequests() { return totalRequests; }
        public void setTotalRequests(long totalRequests) { this.totalRequests = totalRequests; }

        public double getHitRate() { return hitRate; }
        public void setHitRate(double hitRate) { this.hitRate = hitRate; }

        public boolean isCacheEnabled() { return cacheEnabled; }
        public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }
    }
}
