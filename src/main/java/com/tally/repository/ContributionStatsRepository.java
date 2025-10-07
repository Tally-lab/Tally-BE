package com.tally.repository;

import com.tally.domain.ContributionStats;
import java.util.List;
import java.util.Optional;

public interface ContributionStatsRepository {
    ContributionStats save(ContributionStats stats);
    Optional<ContributionStats> findById(String id);
    List<ContributionStats> findByUserId(String userId);
    void delete(String id);
}