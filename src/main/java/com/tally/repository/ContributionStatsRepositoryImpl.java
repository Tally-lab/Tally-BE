package com.tally.repository;

import com.tally.domain.ContributionStats;
import com.tally.util.JsonFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

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
        String fileName = FILE_PREFIX + stats.getId() + FILE_SUFFIX;
        JsonFileUtil.writeToFile(fileName, stats);
        log.info("ContributionStats saved: {}", stats.getId());
        return stats;
    }

    @Override
    public Optional<ContributionStats> findById(String id) {
        String fileName = FILE_PREFIX + id + FILE_SUFFIX;
        ContributionStats stats = JsonFileUtil.readFromFile(fileName, ContributionStats.class);
        return Optional.ofNullable(stats);
    }

    @Override
    public List<ContributionStats> findByUserId(String userId) {
        log.warn("findByUserId not fully implemented");
        return new ArrayList<>();
    }

    @Override
    public void delete(String id) {
        String fileName = FILE_PREFIX + id + FILE_SUFFIX;
        JsonFileUtil.deleteFile(fileName);
        log.info("ContributionStats deleted: {}", id);
    }
}