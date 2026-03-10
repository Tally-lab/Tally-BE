package com.devpulse.mcp.github.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class GitHubGraphQLClient {

    private static final String GITHUB_GRAPHQL_ENDPOINT = "https://api.github.com/graphql";
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public GitHubGraphQLClient() {
        this.webClient = WebClient.builder()
                .baseUrl(GITHUB_GRAPHQL_ENDPOINT)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public JsonNode executeQuery(String token, String query, Map<String, Object> variables) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", query);
            if (variables != null && !variables.isEmpty()) {
                body.put("variables", variables);
            }

            String requestBody = objectMapper.writeValueAsString(body);
            log.debug("GraphQL Query: {}", query);

            Mono<String> responseMono = webClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class);

            String responseBody = responseMono.block();
            JsonNode response = objectMapper.readTree(responseBody);

            if (response.has("errors")) {
                log.error("GraphQL errors: {}", response.get("errors"));
                throw new RuntimeException("GraphQL query failed: " + response.get("errors"));
            }

            return response.get("data");

        } catch (Exception e) {
            log.error("Failed to execute GraphQL query", e);
            throw new RuntimeException("GraphQL execution failed", e);
        }
    }

    public JsonNode getOrganizationAnalysis(String token, String orgName, int maxRepos) {
        String query = """
            query OrganizationAnalysis($org: String!, $maxRepos: Int!) {
              organization(login: $org) {
                login
                repositories(first: $maxRepos, orderBy: {field: UPDATED_AT, direction: DESC}) {
                  nodes {
                    name
                    nameWithOwner
                    url
                    description
                    isPrivate
                    defaultBranchRef {
                      name
                      target {
                        ... on Commit {
                          history(first: 100) {
                            totalCount
                            nodes {
                              oid
                              message
                              committedDate
                              author {
                                name
                                email
                                user {
                                  login
                                  avatarUrl
                                }
                              }
                              additions
                              deletions
                            }
                          }
                        }
                      }
                    }
                    pullRequests(first: 100, states: [OPEN, CLOSED, MERGED], orderBy: {field: CREATED_AT, direction: DESC}) {
                      totalCount
                      nodes {
                        number
                        title
                        state
                        createdAt
                        closedAt
                        mergedAt
                        author {
                          login
                          avatarUrl
                        }
                        additions
                        deletions
                        changedFiles
                        reviews(first: 10) {
                          nodes {
                            state
                            createdAt
                            author {
                              login
                            }
                          }
                        }
                      }
                    }
                    issues(first: 100, states: [OPEN, CLOSED], orderBy: {field: CREATED_AT, direction: DESC}) {
                      totalCount
                      nodes {
                        number
                        title
                        state
                        createdAt
                        closedAt
                        author {
                          login
                        }
                        body
                      }
                    }
                  }
                }
              }
            }
            """;

        Map<String, Object> variables = new HashMap<>();
        variables.put("org", orgName);
        variables.put("maxRepos", maxRepos);

        return executeQuery(token, query, variables);
    }

    public JsonNode getRepositoryAnalysis(String token, String owner, String repo) {
        String query = """
            query RepositoryAnalysis($owner: String!, $repo: String!) {
              repository(owner: $owner, name: $repo) {
                name
                nameWithOwner
                url
                description
                isPrivate
                createdAt
                updatedAt
                defaultBranchRef {
                  name
                  target {
                    ... on Commit {
                      history(first: 100) {
                        totalCount
                        nodes {
                          oid
                          message
                          committedDate
                          author {
                            name
                            email
                            user {
                              login
                              avatarUrl
                            }
                          }
                          additions
                          deletions
                        }
                      }
                    }
                  }
                }
                languages(first: 10, orderBy: {field: SIZE, direction: DESC}) {
                  edges {
                    size
                    node {
                      name
                      color
                    }
                  }
                }
                pullRequests(first: 100, states: [OPEN, CLOSED, MERGED], orderBy: {field: CREATED_AT, direction: DESC}) {
                  totalCount
                  nodes {
                    number
                    title
                    state
                    createdAt
                    closedAt
                    mergedAt
                    author {
                      login
                      avatarUrl
                    }
                    additions
                    deletions
                    changedFiles
                    reviews(first: 10) {
                      nodes {
                        state
                        createdAt
                        author {
                          login
                        }
                      }
                    }
                    body
                  }
                }
                issues(first: 100, states: [OPEN, CLOSED], orderBy: {field: CREATED_AT, direction: DESC}) {
                  totalCount
                  nodes {
                    number
                    title
                    state
                    createdAt
                    closedAt
                    author {
                      login
                    }
                    body
                  }
                }
              }
            }
            """;

        Map<String, Object> variables = new HashMap<>();
        variables.put("owner", owner);
        variables.put("repo", repo);

        return executeQuery(token, query, variables);
    }

    public JsonNode getUserRepositories(String token, int maxRepos) {
        String query = """
            query UserRepositories($maxRepos: Int!) {
              viewer {
                login
                repositories(first: $maxRepos, affiliations: [OWNER, COLLABORATOR, ORGANIZATION_MEMBER], orderBy: {field: UPDATED_AT, direction: DESC}) {
                  totalCount
                  nodes {
                    name
                    nameWithOwner
                    url
                    description
                    isPrivate
                    owner {
                      login
                      avatarUrl
                      __typename
                    }
                    updatedAt
                  }
                }
              }
            }
            """;

        Map<String, Object> variables = new HashMap<>();
        variables.put("maxRepos", maxRepos);

        return executeQuery(token, query, variables);
    }

    public JsonNode getUserOrganizations(String token, int maxOrgs) {
        String query = """
            query UserOrganizations($maxOrgs: Int!) {
              viewer {
                organizations(first: $maxOrgs) {
                  totalCount
                  nodes {
                    login
                    name
                    avatarUrl
                    description
                    url
                  }
                }
              }
            }
            """;

        Map<String, Object> variables = new HashMap<>();
        variables.put("maxOrgs", maxOrgs);

        return executeQuery(token, query, variables);
    }
}
