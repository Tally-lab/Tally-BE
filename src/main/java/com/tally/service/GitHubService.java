package com.tally.service;

import com.tally.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class GitHubService {
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 사용자의 개인 레포지토리 목록 조회 (조직 레포 포함)
     */
    public List<GitHubRepository> getUserRepositories(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        ResponseEntity<GitHubRepository[]> response = restTemplate.exchange(
                "https://api.github.com/user/repos?affiliation=owner,collaborator,organization_member&per_page=100&sort=updated",
                HttpMethod.GET,
                request,
                GitHubRepository[].class
        );

        if (response.getBody() != null) {
            return Arrays.asList(response.getBody());
        }

        return new ArrayList<>();
    }

    /**
     * 사용자가 속한 조직 목록 조회
     */
    public List<Organization> getUserOrganizations(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<Organization[]> response = restTemplate.exchange(
                    "https://api.github.com/user/orgs?per_page=100",
                    HttpMethod.GET,
                    request,
                    Organization[].class
            );

            if (response.getBody() != null) {
                log.info("Found {} organizations from API", response.getBody().length);
                return Arrays.asList(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to fetch user organizations", e);
        }

        return new ArrayList<>();
    }

    /**
     * 레포지토리 owner 정보에서 조직 목록 추출
     * (API /user/orgs가 제대로 작동하지 않을 때 대안)
     */
    public List<Organization> getOrganizationsFromRepositories(String token) {
        List<GitHubRepository> repos = getUserRepositories(token);
        Map<String, Organization> orgMap = new HashMap<>();

        for (GitHubRepository repo : repos) {
            // 조직 소유 레포
            if (repo.getOwner() != null && "Organization".equals(repo.getOwner().getType())) {
                String orgLogin = repo.getOwner().getLogin();

                if (!orgMap.containsKey(orgLogin)) {
                    Organization org = new Organization();
                    org.setId(repo.getOwner().getId());
                    org.setLogin(orgLogin);
                    org.setAvatarUrl(repo.getOwner().getAvatarUrl());
                    org.setDescription("조직 레포지토리");
                    orgMap.put(orgLogin, org);
                }
            }

            // Fork의 원본이 조직인 경우
            if (repo.getFork() != null && repo.getFork() &&
                    repo.getParent() != null &&
                    repo.getParent().getOwner() != null &&
                    "Organization".equals(repo.getParent().getOwner().getType())) {

                String orgLogin = repo.getParent().getOwner().getLogin();

                if (!orgMap.containsKey(orgLogin)) {
                    Organization org = new Organization();
                    org.setId(repo.getParent().getOwner().getId());
                    org.setLogin(orgLogin);
                    org.setAvatarUrl(repo.getParent().getOwner().getAvatarUrl());
                    org.setDescription("Fork된 레포지토리 원본 조직");
                    orgMap.put(orgLogin, org);
                }
            }
        }

        List<Organization> organizations = new ArrayList<>(orgMap.values());
        log.info("Extracted {} organizations from repositories", organizations.size());
        return organizations;
    }

    /**
     * 특정 조직의 레포지토리 목록 조회
     */
    public List<GitHubRepository> getOrganizationRepositories(String token, String org) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<GitHubRepository[]> response = restTemplate.exchange(
                    String.format("https://api.github.com/orgs/%s/repos?per_page=100&sort=updated", org),
                    HttpMethod.GET,
                    request,
                    GitHubRepository[].class
            );

            if (response.getBody() != null) {
                log.info("Found {} repositories in organization {}", response.getBody().length, org);
                return Arrays.asList(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to fetch repositories for organization: {}", org, e);
        }

        return new ArrayList<>();
    }

    /**
     * 사용자의 모든 레포지토리 조회 (개인 + 조직의 모든 레포)
     */
    public List<GitHubRepository> getAllRepositories(String token) {
        List<GitHubRepository> allRepos = new ArrayList<>();

        // 1. 개인 레포 + 직접 기여한 레포
        log.info("Fetching user repositories...");
        List<GitHubRepository> userRepos = getUserRepositories(token);
        allRepos.addAll(userRepos);
        log.info("Found {} user repositories", userRepos.size());

        // 2. 속한 조직의 모든 레포
        log.info("Fetching organizations...");
        List<Organization> orgs = getUserOrganizations(token);

        for (Organization org : orgs) {
            log.info("Fetching repositories for organization: {}", org.getLogin());
            List<GitHubRepository> orgRepos = getOrganizationRepositories(token, org.getLogin());
            allRepos.addAll(orgRepos);
        }

        // 3. 중복 제거 (같은 레포가 여러 번 나올 수 있음)
        List<GitHubRepository> uniqueRepos = allRepos.stream()
                .distinct()
                .collect(Collectors.toList());

        log.info("Total repositories after deduplication: {}", uniqueRepos.size());
        return uniqueRepos;
    }

    /**
     * 특정 조직에서 사용자가 기여한 레포지토리만 필터링
     */
    public List<GitHubRepository> getUserRepositoriesInOrganization(String token, String orgName, String username) {
        List<GitHubRepository> orgRepos = getOrganizationRepositories(token, orgName);
        List<GitHubRepository> contributedRepos = new ArrayList<>();

        for (GitHubRepository repo : orgRepos) {
            // 각 레포의 커밋을 확인하여 사용자가 기여했는지 체크
            List<Commit> commits = getRepositoryCommits(token, orgName, repo.getName());

            boolean hasContribution = commits.stream()
                    .anyMatch(commit -> {
                        // author가 있고 login이 일치하는지 확인
                        if (commit.getAuthor() != null && commit.getAuthor().getLogin() != null) {
                            return username.equalsIgnoreCase(commit.getAuthor().getLogin());
                        }
                        // commit.commit.author의 이름으로도 확인
                        if (commit.getCommit() != null && commit.getCommit().getAuthor() != null) {
                            String authorName = commit.getCommit().getAuthor().getName();
                            return authorName != null && authorName.equalsIgnoreCase(username);
                        }
                        return false;
                    });

            if (hasContribution) {
                contributedRepos.add(repo);
            }
        }

        log.info("User {} contributed to {}/{} repositories in organization {}",
                username, contributedRepos.size(), orgRepos.size(), orgName);
        return contributedRepos;
    }

    /**
     * 레포지토리의 모든 브랜치 목록 조회
     */
    public List<String> getRepositoryBranches(String token, String owner, String repo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        String url = String.format("https://api.github.com/repos/%s/%s/branches?per_page=100", owner, repo);

        try {
            ResponseEntity<Map[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    Map[].class
            );

            if (response.getBody() != null) {
                return Arrays.stream(response.getBody())
                        .map(branch -> (String) branch.get("name"))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("Failed to fetch branches for {}/{}: {}", owner, repo, e.getMessage());
        }

        return new ArrayList<>();
    }

    /**
     * 레포지토리의 커밋 목록 조회 (모든 브랜치에서)
     */
    public List<Commit> getRepositoryCommits(String token, String owner, String repo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        // 1. 모든 브랜치 가져오기
        List<String> branches = getRepositoryBranches(token, owner, repo);
        log.info("Found {} branches in {}/{}", branches.size(), owner, repo);

        // 2. 각 브랜치에서 커밋 가져오기 (중복 제거용 Set)
        Set<String> seenShas = new HashSet<>();
        List<Commit> allCommits = new ArrayList<>();

        // 브랜치가 너무 많으면 주요 브랜치만 (성능 고려)
        List<String> targetBranches = branches.size() > 10
            ? branches.stream().limit(10).collect(Collectors.toList())
            : branches;

        for (String branch : targetBranches) {
            String url = String.format("https://api.github.com/repos/%s/%s/commits?sha=%s&per_page=100",
                    owner, repo, branch);

            try {
                ResponseEntity<Commit[]> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        request,
                        Commit[].class
                );

                if (response.getBody() != null) {
                    for (Commit commit : response.getBody()) {
                        // SHA로 중복 제거
                        if (commit.getSha() != null && !seenShas.contains(commit.getSha())) {
                            seenShas.add(commit.getSha());
                            allCommits.add(commit);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to fetch commits for branch {} in {}/{}: {}", branch, owner, repo, e.getMessage());
            }
        }

        log.info("Total unique commits from {} branches: {}", targetBranches.size(), allCommits.size());
        return allCommits;
    }

    /**
     * 커밋 상세 정보 조회 (파일 목록 포함)
     */
    public Commit getCommitDetail(String token, String owner, String repo, String sha) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        String url = String.format("https://api.github.com/repos/%s/%s/commits/%s", owner, repo, sha);

        try {
            ResponseEntity<Commit> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    Commit.class
            );

            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch commit detail for sha: {}", sha, e);
            return null;
        }
    }

    /**
     * 레포지토리의 Pull Request 목록 조회
     */
    public List<PullRequest> getRepositoryPullRequests(String token, String owner, String repo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        String url = String.format("https://api.github.com/repos/%s/%s/pulls?state=all&per_page=100", owner, repo);

        try {
            ResponseEntity<PullRequest[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    PullRequest[].class
            );

            if (response.getBody() != null) {
                return Arrays.asList(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to fetch pull requests for {}/{}: {}", owner, repo, e.getMessage());
        }

        return new ArrayList<>();
    }

    /**
     * 레포지토리의 Issue 목록 조회
     */
    public List<Issue> getRepositoryIssues(String token, String owner, String repo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "token " + token);
        headers.set("Accept", "application/json");

        HttpEntity<String> request = new HttpEntity<>(headers);

        String url = String.format("https://api.github.com/repos/%s/%s/issues?state=all&per_page=100", owner, repo);

        try {
            ResponseEntity<Issue[]> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    request,
                    Issue[].class
            );

            if (response.getBody() != null) {
                return Arrays.asList(response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to fetch issues for {}/{}: {}", owner, repo, e.getMessage());
        }

        return new ArrayList<>();
    }
}