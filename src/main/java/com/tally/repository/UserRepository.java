package com.tally.repository;

import com.tally.domain.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(String id);
    Optional<User> findByAccessToken(String accessToken);
    List<User> findAll();
    void deleteById(String id);
}