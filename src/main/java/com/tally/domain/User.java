package com.tally.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private String id;
    private String phoneNumber;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}