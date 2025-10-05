package com.tally.service;

import com.tally.domain.ContributionStats;
import com.tally.repository.ContributionStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContributionAnalysisService {

    private final ContributionStatsRepository contributionStatsRepository;
    private final GitHubService gitHubService;

    public ContributionStats analyzeContribution(String userId, String accessToken, String owner, String repo, String userLogin) {
        log.info("Analyzing contribution for user {} in repo {}/{}", userLogin, owner, repo);

        // GitHub에서 커밋 데이터 가져오기
        Map<String, Object> repoData = gitHubService.getRepositoryCommits(accessToken, owner, repo);
        List<Map<String, Object>> commits = (List<Map<String, Object>>) repoData.get("commits");

        // 사용자 커밋 분석
        int totalCommits = commits.size();
        int userCommits = 0;
        int additions = 0;
        int deletions = 0;
        Map<Integer, Integer> hourlyActivity = new HashMap<>();
        Map<String, Integer> dailyActivity = new HashMap<>();

        for (Map<String, Object> commit : commits) {
            Map<String, Object> commitData = (Map<String, Object>) commit.get("commit");
            Map<String, Object> author = (Map<String, Object>) commitData.get("author");
            String authorName = (String) author.get("name");

            // 이 사용자의 커밋인지 확인 (간단한 비교, 실제로는 더 정교한 로직 필요)
            if (authorName != null && authorName.toLowerCase().contains(userLogin.toLowerCase())) {
                userCommits++;

                // 시간대별 활동 (실제로는 날짜 파싱 필요)
                int hour = new Random().nextInt(24); // 임시
                hourlyActivity.merge(hour, 1, Integer::sum);
            }
        }

        double commitPercentage = totalCommits > 0 ? (userCommits * 100.0 / totalCommits) : 0;

        // 통계 저장
        ContributionStats stats = ContributionStats.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .repositoryFullName(owner + "/" + repo)
                .totalCommits(totalCommits)
                .userCommits(userCommits)
                .commitPercentage(commitPercentage)
                .additions(additions)
                .deletions(deletions)
                .languageDistribution(new HashMap<>()) // 추후 구현
                .hourlyActivity(hourlyActivity)
                .dailyActivity(dailyActivity)
                .analyzedAt(LocalDateTime.now())
                .build();

        contributionStatsRepository.save(stats);
        log.info("Analysis completed: {}% contribution", String.format("%.2f", commitPercentage));

        return stats;
    }

    public ContributionStats getStats(String statsId) {
        return contributionStatsRepository.findById(statsId)
                .orElseThrow(() -> new RuntimeException("Stats not found: " + statsId));
    }
}