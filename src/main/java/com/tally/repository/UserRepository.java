package com.tally.repository;

import com.tally.domain.User;
import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(String id);
    Optional<User> findByPhoneNumber(String phoneNumber);
    void delete(String id);
}