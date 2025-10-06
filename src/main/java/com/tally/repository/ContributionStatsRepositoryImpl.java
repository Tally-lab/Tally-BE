package com.tally.repository;

import com.tally.domain.ContributionStats;
import com.tally.util.JsonFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class ContributionStatsRepositoryImpl implements ContributionStatsRepository {

    private static final String FILE_PREFIX = "stats_";
    private static final String FILE_SUFFIX = ".json";

    @Override
    public ContributionStats save(ContributionStats stats) {
        try {
            String fileName = FILE_PREFIX + stats.getId() + FILE_SUFFIX;
            JsonFileUtil.writeToFile(fileName, stats);
            log.info("ContributionStats saved: {}", stats.getId());
            return stats;
        } catch (IOException e) {
            log.error("Failed to save stats", e);
            throw new RuntimeException("Failed to save stats", e);
        }
    }

    @Override
    public Optional<ContributionStats> findById(String id) {
        try {
            String fileName = FILE_PREFIX + id + FILE_SUFFIX;
            ContributionStats stats = JsonFileUtil.readFromFile(fileName, ContributionStats.class);
            return Optional.ofNullable(stats);
        } catch (IOException e) {
            log.error("Failed to find stats by id: {}", id, e);
            return Optional.empty();
        }
    }

    @Override
    public List<ContributionStats> findByUserId(String userId) {
        log.warn("findByUserId not fully implemented");
        return new ArrayList<>();
    }

    @Override
    public void delete(String id) {
        try {
            String fileName = FILE_PREFIX + id + FILE_SUFFIX;
            JsonFileUtil.deleteFile(fileName);
            log.info("ContributionStats deleted: {}", id);
        } catch (IOException e) {
            log.error("Failed to delete stats", e);
            throw new RuntimeException("Failed to delete stats", e);
        }
    }
}