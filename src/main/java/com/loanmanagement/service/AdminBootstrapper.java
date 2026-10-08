package com.loanmanagement.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import com.loanmanagement.dao.AdminBootstrapRepository;
import com.loanmanagement.dao.UserRepository;
import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;
import com.loanmanagement.security.PasswordHasher;
import com.loanmanagement.validation.InputValidator;

public final class AdminBootstrapper {
    private final UserRepository users;
    private final AdminBootstrapRepository bootstrapRepository;
    private final PasswordHasher passwordHasher;

    public AdminBootstrapper(
            UserRepository users,
            AdminBootstrapRepository bootstrapRepository,
            PasswordHasher passwordHasher) {
        this.users = users;
        this.bootstrapRepository = bootstrapRepository;
        this.passwordHasher = passwordHasher;
    }

    public boolean bootstrapFromEnvironment() {
        String username = System.getenv("ADMIN_USERNAME");
        String email = System.getenv("ADMIN_EMAIL");
        String configuredPassword = System.getenv("ADMIN_PASSWORD");
        if (isBlank(username) && isBlank(email) && isBlank(configuredPassword)) {
            return false;
        }
        if (isBlank(username) || isBlank(email) || isBlank(configuredPassword)
                || !InputValidator.isValidEmail(email)
                || !InputValidator.isValidPassword(configuredPassword)) {
            throw new IllegalStateException("Configure valid ADMIN_USERNAME, ADMIN_EMAIL, and ADMIN_PASSWORD together");
        }

        String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Optional<User> existing = users.findByLoginIdentifier(normalizedUsername);
        if (existing.isPresent()) {
            User account = existing.get();
            if (account.role() == UserRole.ROLE_ADMIN && account.email().equals(normalizedEmail)) {
                return false;
            }
            throw new IllegalStateException("Configured admin username is already assigned to another account");
        }
        if (users.existsByEmail(normalizedEmail)) {
            throw new IllegalStateException("Configured admin email is already assigned to another account");
        }

        char[] password = configuredPassword.toCharArray();
        try {
            bootstrapRepository.createAdmin(normalizedUsername, normalizedEmail, passwordHasher.hash(password));
            return true;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}