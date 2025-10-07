package com.tally.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tally.domain.User;
import com.tally.util.JsonFileUtil;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class UserRepositoryImpl implements UserRepository {
    private static final String FILE_PATH = "users.json";  // "data/" 제거!

    @Override
    public User save(User user) {
        List<User> users = findAll();

        if (user.getId() == null) {
            user.setId(UUID.randomUUID().toString());
        }

        users.removeIf(u -> u.getId().equals(user.getId()));
        users.add(user);

        JsonFileUtil.writeToFile(FILE_PATH, users);
        return user;
    }

    @Override
    public Optional<User> findById(String id) {
        return findAll().stream()
                .filter(user -> user.getId().equals(id))
                .findFirst();
    }

    @Override
    public Optional<User> findByAccessToken(String accessToken) {
        return findAll().stream()
                .filter(user -> accessToken.equals(user.getAccessToken()))
                .findFirst();
    }

    @Override
    public List<User> findAll() {
        return JsonFileUtil.readFromFile(FILE_PATH, new TypeReference<List<User>>() {});
    }

    @Override
    public void deleteById(String id) {
        List<User> users = findAll();
        users.removeIf(user -> user.getId().equals(id));
        JsonFileUtil.writeToFile(FILE_PATH, users);
    }
}