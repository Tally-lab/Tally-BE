package com.tally.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class Commit {
    private String sha;

    @JsonProperty("node_id")
    private String nodeId;

    private CommitDetail commit;

    private String url;

    @JsonProperty("html_url")
    private String htmlUrl;

    private CommitAuthor author;

    private CommitAuthor committer;

    private List<CommitFile> files;

    @Data
    public static class CommitDetail {
        private GitUser author;
        private GitUser committer;
        private String message;

        @JsonProperty("comment_count")
        private Integer commentCount;
    }

    @Data
    public static class GitUser {
        private String name;
        private String email;
        private String date;
    }

    @Data
    public static class CommitAuthor {
        private String login;
        private Long id;

        @JsonProperty("node_id")
        private String nodeId;

        @JsonProperty("avatar_url")
        private String avatarUrl;

        private String url;

        @JsonProperty("html_url")
        private String htmlUrl;

        private String type;
    }

    @Data
    public static class CommitFile {
        private String sha;
        private String filename;
        private String status;
        private Integer additions;
        private Integer deletions;
        private Integer changes;

        @JsonProperty("blob_url")
        private String blobUrl;

        @JsonProperty("raw_url")
        private String rawUrl;

        @JsonProperty("contents_url")
        private String contentsUrl;

        private String patch;
    }
}