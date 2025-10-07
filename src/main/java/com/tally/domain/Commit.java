package com.tally.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Commit {
    private String sha;

    @JsonProperty("commit")
    private CommitDetail commit;

    private User author;
    private User committer;

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
}