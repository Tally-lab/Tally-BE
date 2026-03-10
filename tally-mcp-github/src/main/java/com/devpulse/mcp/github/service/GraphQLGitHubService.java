package com.devpulse.mcp.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.devpulse.mcp.github.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Service
public class GraphQLGitHubService {

    private final GitHubGraphQLClient graphQLClient;

    public GraphQLGitHubService(GitHubGraphQLClient graphQLClient) {
        this.graphQLClient = graphQLClient;
    }

    public List<GitHubRepository> getOrganizationRepositories(String token, String org) {
        log.info("Fetching repositories for organization: {}", org);
        JsonNode data = graphQLClient.getOrganizationAnalysis(token, org, 100);
        JsonNode orgNode = data.get("organization");

        if (orgNode == null) {
            log.warn("Organization not found: {}", org);
            return new ArrayList<>();
        }

        return parseRepositories(orgNode.get("repositories").get("nodes"));
    }

    public Map<String, RepositoryData> getOrganizationFullAnalysis(String token, String org) {
        log.info("Fetching full analysis for organization: {}", org);
        JsonNode data = graphQLClient.getOrganizationAnalysis(token, org, 100);
        JsonNode orgNode = data.get("organization");

        if (orgNode == null) {
            throw new RuntimeException("Organization not found: " + org);
        }

        JsonNode reposNode = orgNode.get("repositories").get("nodes");
        Map<String, RepositoryData> result = new HashMap<>();

        for (JsonNode repoNode : reposNode) {
            String repoName = repoNode.get("name").asText();
            result.put(repoName, parseRepositoryData(repoNode));
        }

        log.info("Fetched data for {} repositories in organization {}", result.size(), org);
        return result;
    }

    public RepositoryData getRepositoryAnalysis(String token, String owner, String repo) {
        log.info("Fetching repository analysis: {}/{}", owner, repo);
        JsonNode data = graphQLClient.getRepositoryAnalysis(token, owner, repo);
        JsonNode repoNode = data.get("repository");

        if (repoNode == null) {
            throw new RuntimeException("Repository not found: " + owner + "/" + repo);
        }

        return parseRepositoryData(repoNode);
    }

    public List<GitHubRepository> getUserRepositories(String token) {
        log.info("Fetching user repositories");
        JsonNode data = graphQLClient.getUserRepositories(token, 100);
        return parseRepositories(data.get("viewer").get("repositories").get("nodes"));
    }

    public List<Organization> getUserOrganizations(String token) {
        log.info("Fetching user organizations");
        JsonNode data = graphQLClient.getUserOrganizations(token, 100);
        JsonNode orgsNode = data.get("viewer").get("organizations").get("nodes");

        List<Organization> organizations = new ArrayList<>();
        for (JsonNode orgNode : orgsNode) {
            Organization org = new Organization();
            org.setLogin(orgNode.get("login").asText());
            org.setAvatarUrl(orgNode.has("avatarUrl") ? orgNode.get("avatarUrl").asText() : null);
            org.setDescription(orgNode.has("description") ? orgNode.get("description").asText() : null);
            org.setUrl(orgNode.get("url").asText());
            organizations.add(org);
        }

        log.info("Found {} organizations", organizations.size());
        return organizations;
    }

    // === Private Helpers ===

    private List<GitHubRepository> parseRepositories(JsonNode reposNode) {
        List<GitHubRepository> repos = new ArrayList<>();
        for (JsonNode node : reposNode) {
            GitHubRepository repo = new GitHubRepository();
            repo.setName(node.get("name").asText());
            repo.setFullName(node.get("nameWithOwner").asText());
            repo.setUrl(node.get("url").asText());
            repo.setDescription(node.has("description") && !node.get("description").isNull()
                    ? node.get("description").asText() : "");
            repo.setIsPrivate(node.get("isPrivate").asBoolean());

            if (node.has("owner")) {
                JsonNode ownerNode = node.get("owner");
                GitHubRepository.Owner owner = new GitHubRepository.Owner();
                owner.setLogin(ownerNode.get("login").asText());
                owner.setAvatarUrl(ownerNode.has("avatarUrl") ? ownerNode.get("avatarUrl").asText() : null);
                owner.setType(ownerNode.get("__typename").asText());
                repo.setOwner(owner);
            }

            repos.add(repo);
        }
        return repos;
    }

    private RepositoryData parseRepositoryData(JsonNode repoNode) {
        RepositoryData data = new RepositoryData();
        data.setName(repoNode.get("name").asText());
        data.setFullName(repoNode.get("nameWithOwner").asText());
        data.setUrl(repoNode.get("url").asText());

        // Commits
        if (repoNode.has("defaultBranchRef") && !repoNode.get("defaultBranchRef").isNull()) {
            JsonNode branchRef = repoNode.get("defaultBranchRef");
            if (branchRef.has("target")) {
                JsonNode historyNode = branchRef.get("target").get("history");
                data.setCommits(parseCommits(historyNode.get("nodes")));
                data.setTotalCommitCount(historyNode.get("totalCount").asInt());
            }
        } else {
            data.setCommits(new ArrayList<>());
            data.setTotalCommitCount(0);
        }

        // Pull Requests
        if (repoNode.has("pullRequests")) {
            JsonNode prNode = repoNode.get("pullRequests");
            data.setPullRequests(parsePullRequests(prNode.get("nodes")));
            data.setTotalPRCount(prNode.get("totalCount").asInt());
        } else {
            data.setPullRequests(new ArrayList<>());
            data.setTotalPRCount(0);
        }

        // Issues
        if (repoNode.has("issues")) {
            JsonNode issueNode = repoNode.get("issues");
            data.setIssues(parseIssues(issueNode.get("nodes")));
            data.setTotalIssueCount(issueNode.get("totalCount").asInt());
        } else {
            data.setIssues(new ArrayList<>());
            data.setTotalIssueCount(0);
        }

        // Languages
        if (repoNode.has("languages")) {
            data.setLanguageDistribution(parseLanguages(repoNode.get("languages").get("edges")));
        }

        return data;
    }

    private List<Commit> parseCommits(JsonNode commitsNode) {
        List<Commit> commits = new ArrayList<>();
        for (JsonNode node : commitsNode) {
            Commit commit = new Commit();
            commit.setSha(node.get("oid").asText());

            Commit.CommitDetail detail = new Commit.CommitDetail();
            detail.setMessage(node.get("message").asText());

            if (node.has("author") && !node.get("author").isNull()) {
                JsonNode authorNode = node.get("author");
                Commit.GitUser gitUser = new Commit.GitUser();
                gitUser.setName(authorNode.has("name") ? authorNode.get("name").asText() : "Unknown");
                gitUser.setEmail(authorNode.has("email") ? authorNode.get("email").asText() : "");
                gitUser.setDate(node.get("committedDate").asText());
                detail.setAuthor(gitUser);

                if (authorNode.has("user") && !authorNode.get("user").isNull()) {
                    JsonNode userNode = authorNode.get("user");
                    Commit.CommitAuthor commitAuthor = new Commit.CommitAuthor();
                    commitAuthor.setLogin(userNode.get("login").asText());
                    commitAuthor.setAvatarUrl(userNode.has("avatarUrl") ? userNode.get("avatarUrl").asText() : null);
                    commit.setAuthor(commitAuthor);
                }
            }

            commit.setCommit(detail);
            commits.add(commit);
        }
        return commits;
    }

    private List<PullRequest> parsePullRequests(JsonNode prsNode) {
        List<PullRequest> prs = new ArrayList<>();
        for (JsonNode node : prsNode) {
            PullRequest pr = new PullRequest();
            pr.setNumber((long) node.get("number").asInt());
            pr.setTitle(node.get("title").asText());
            pr.setState(node.get("state").asText());
            pr.setHtmlUrl("");

            pr.setCreatedAt(parseDateTime(node.get("createdAt").asText()));
            if (node.has("closedAt") && !node.get("closedAt").isNull()) {
                pr.setClosedAt(parseDateTime(node.get("closedAt").asText()));
            }
            if (node.has("mergedAt") && !node.get("mergedAt").isNull()) {
                pr.setMergedAt(parseDateTime(node.get("mergedAt").asText()));
            }

            if (node.has("author") && !node.get("author").isNull()) {
                JsonNode authorNode = node.get("author");
                User user = new User();
                user.setUsername(authorNode.get("login").asText());
                user.setAvatarUrl(authorNode.has("avatarUrl") ? authorNode.get("avatarUrl").asText() : null);
                pr.setUser(user);
            }

            if (node.has("body") && !node.get("body").isNull()) {
                pr.setBody(node.get("body").asText());
            }

            prs.add(pr);
        }
        return prs;
    }

    private List<Issue> parseIssues(JsonNode issuesNode) {
        List<Issue> issues = new ArrayList<>();
        for (JsonNode node : issuesNode) {
            Issue issue = new Issue();
            issue.setNumber((long) node.get("number").asInt());
            issue.setTitle(node.get("title").asText());
            issue.setState(node.get("state").asText());

            issue.setCreatedAt(parseDateTime(node.get("createdAt").asText()));
            if (node.has("closedAt") && !node.get("closedAt").isNull()) {
                issue.setClosedAt(parseDateTime(node.get("closedAt").asText()));
            }

            if (node.has("author") && !node.get("author").isNull()) {
                JsonNode authorNode = node.get("author");
                User user = new User();
                user.setUsername(authorNode.get("login").asText());
                issue.setUser(user);
            }

            if (node.has("body") && !node.get("body").isNull()) {
                issue.setBody(node.get("body").asText());
            }

            issues.add(issue);
        }
        return issues;
    }

    private Map<String, Integer> parseLanguages(JsonNode languagesNode) {
        Map<String, Integer> languages = new HashMap<>();
        for (JsonNode edge : languagesNode) {
            languages.put(edge.get("node").get("name").asText(), edge.get("size").asInt());
        }
        return languages;
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            return ZonedDateTime.parse(dateTimeStr).toLocalDateTime();
        } catch (Exception e) {
            log.warn("Failed to parse datetime: {}", dateTimeStr);
            return null;
        }
    }

    // Repository data holder
    @lombok.Data
    public static class RepositoryData {
        private String name;
        private String fullName;
        private String url;
        private List<Commit> commits;
        private List<PullRequest> pullRequests;
        private List<Issue> issues;
        private int totalCommitCount;
        private int totalPRCount;
        private int totalIssueCount;
        private Map<String, Integer> languageDistribution;
    }
}
