package com.loanmanagement.model;

import java.time.Instant;

public record User(
        long id,
        String firstName,
        String lastName,
        String mobile,
        String email,
        String location,
        String username,
        String passwordHash,
        boolean mustChangePassword,
        UserRole role,
        AccountStatus status,
        int failedAttempts,
        String pan,
        Integer cibilScore,
        Instant createdAt,
        Instant lastModifiedAt) {
}