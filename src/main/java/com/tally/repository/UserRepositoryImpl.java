package com.tally.repository;

import com.tally.domain.User;
import com.tally.util.JsonFileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Repository
public class UserRepositoryImpl implements UserRepository {

    private static final String FILE_PREFIX = "user_";
    private static final String FILE_SUFFIX = ".json";

    @Override
    public User save(User user) {
        try {
            String fileName = FILE_PREFIX + user.getPhoneNumber() + FILE_SUFFIX;
            JsonFileUtil.writeToFile(fileName, user);
            log.info("User saved: {}", user.getPhoneNumber());
            return user;
        } catch (IOException e) {
            log.error("Failed to save user", e);
            throw new RuntimeException("Failed to save user", e);
        }
    }

    @Override
    public Optional<User> findById(String id) {
        // ID로 찾기는 전화번호로 찾는 것과 동일하게 처리
        return findByPhoneNumber(id);
    }

    @Override
    public Optional<User> findByPhoneNumber(String phoneNumber) {
        try {
            String fileName = FILE_PREFIX + phoneNumber + FILE_SUFFIX;
            User user = JsonFileUtil.readFromFile(fileName, User.class);
            return Optional.ofNullable(user);
        } catch (IOException e) {
            log.error("Failed to find user by phone number: {}", phoneNumber, e);
            return Optional.empty();
        }
    }

    @Override
    public void delete(String id) {
        try {
            String fileName = FILE_PREFIX + id + FILE_SUFFIX;
            JsonFileUtil.deleteFile(fileName);
            log.info("User deleted: {}", id);
        } catch (IOException e) {
            log.error("Failed to delete user", e);
            throw new RuntimeException("Failed to delete user", e);
        }
    }
}