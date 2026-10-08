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

    /** Copy with the temporary-password flag changed. Records are immutable, so screens use this after a rotation. */
    public User withMustChangePassword(boolean mustChange) {
        return new User(id, firstName, lastName, mobile, email, location, username, passwordHash, mustChange,
                role, status, failedAttempts, pan, cibilScore, createdAt, lastModifiedAt);
    }
}
