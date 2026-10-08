package com.loanmanagement.service;

import java.util.Optional;

import com.loanmanagement.model.User;

public record AuthenticationResult(
        AuthenticationStatus status,
        Optional<User> user,
        int attemptsRemaining) {
}