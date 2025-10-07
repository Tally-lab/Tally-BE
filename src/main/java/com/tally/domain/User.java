package com.tally.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class User {
    private String id;
    private String username;
    private String accessToken;
    private String email;
    private String avatarUrl;
}