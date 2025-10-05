package com.tally.service;

import com.tally.domain.User;
import com.tally.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    public User login(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber)
                .map(user -> {
                    // 기존 사용자 - 마지막 로그인 시간 업데이트
                    User updatedUser = User.builder()
                            .id(user.getId())
                            .phoneNumber(user.getPhoneNumber())
                            .createdAt(user.getCreatedAt())
                            .lastLoginAt(LocalDateTime.now())
                            .build();
                    userRepository.save(updatedUser);
                    log.info("Existing user logged in: {}", phoneNumber);
                    return updatedUser;
                })
                .orElseGet(() -> {
                    // 신규 사용자 - 자동 계정 생성
                    User newUser = User.builder()
                            .id(UUID.randomUUID().toString())
                            .phoneNumber(phoneNumber)
                            .createdAt(LocalDateTime.now())
                            .lastLoginAt(LocalDateTime.now())
                            .build();
                    userRepository.save(newUser);
                    log.info("New user created: {}", phoneNumber);
                    return newUser;
                });
    }

    public User getUserByPhoneNumber(String phoneNumber) {
        return userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new RuntimeException("User not found: " + phoneNumber));
    }
}