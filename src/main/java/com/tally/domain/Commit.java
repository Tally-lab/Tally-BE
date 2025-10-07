package com.tally.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Commit {
    private String sha;

    @JsonProperty("commit")
    private CommitDetail commit;

    private User author;
    private User committer;

    // 파일 정보 추가
    private List<CommitFile> files;

    @Getter
    @Setter
    public static class CommitDetail {
        private Author author;
        private Author committer;
        private String message;

        @Getter
        @Setter
        public static class Author {
            private String name;
            private String email;
            private String date;
        }
    }

    @Getter
    @Setter
    public static class CommitFile {
        private String filename;
        private String status;  // added, modified, removed
        private int additions;
        private int deletions;
        private int changes;

        @JsonProperty("blob_url")
        private String blobUrl;
    }
}