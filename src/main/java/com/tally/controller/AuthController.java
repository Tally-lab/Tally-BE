package com.tally.controller;

import com.tally.domain.User;
import com.tally.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<User> login(@RequestBody Map<String, String> request) {
        String phoneNumber = request.get("phoneNumber");
        log.info("Login request for phone number: {}", phoneNumber);

        User user = authService.login(phoneNumber);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/user/{phoneNumber}")
    public ResponseEntity<User> getUser(@PathVariable String phoneNumber) {
        User user = authService.getUserByPhoneNumber(phoneNumber);
        return ResponseEntity.ok(user);
    }
}