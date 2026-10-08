package com.loanmanagement.service;

import com.loanmanagement.dao.CibilRepository;
import com.loanmanagement.dao.UserRepository;
import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.LoanType;
import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;
import com.loanmanagement.notification.EmailDeliveryException;
import com.loanmanagement.notification.EmailSender;
import com.loanmanagement.security.PasswordHasher;
import com.loanmanagement.validation.InputValidator;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Locale;

public final class CibilService {
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final CibilRepository cibilRepository;
    private final AuthenticationService authentication;
    private final InterestRatePolicy interestRates;
    private final EmailSender emailSender;

    public CibilService(
            UserRepository users,
            CibilRepository cibilRepository,
            AuthenticationService authentication,
            InterestRatePolicy interestRates,
            EmailSender emailSender) {
        this.users = users;
        this.cibilRepository = cibilRepository;
        this.authentication = authentication;
        this.interestRates = interestRates;
        this.emailSender = emailSender;
    }

    public CibilAssessmentResult assess(User user, String pan, LoanType loanType) {
        requireActiveUser(user);
        validatePanAndLoanType(pan, loanType);
        int score = RANDOM.nextInt(600, 901);
        cibilRepository.updateCibil(user.id(), pan.trim(), score);
        User updated = withCibil(user, pan.trim(), score);
        return new CibilAssessmentResult(updated, interestRates.rateFor(loanType, score));
    }

    public CibilOnboardingResult onboard(CibilOnboardingCommand command) {
        validateOnboarding(command);
        int score = RANDOM.nextInt(600, 901);
        String username = generatedUsername(command);
        char[] temporaryPassword = generateTemporaryPassword();
        try {
            RegisterCommand registration = new RegisterCommand(
                    command.firstName().trim(), command.lastName().trim(), command.mobile().trim(),
                    command.email().trim(), command.location().trim(), username, temporaryPassword);
            User user = authentication.registerGenerated(registration, command.pan().trim(), score);
            BigDecimal interestRate = interestRates.rateFor(command.loanType(), score);
            String recoveryPassword = new String(temporaryPassword);
            try {
                emailSender.send(user.email(), "Your Username and Password for Loan Application",
                        "Your username is " + username + " and your temporary password is " + recoveryPassword
                                + ". You must choose a new password at first login.");
                return new CibilOnboardingResult(user, interestRate, true, null);
            } catch (EmailDeliveryException exception) {
                return new CibilOnboardingResult(user, interestRate, false, recoveryPassword);
            }
        } finally {
            Arrays.fill(temporaryPassword, '\0');
        }
    }

    private void validateOnboarding(CibilOnboardingCommand command) {
        if (command == null
                || !InputValidator.isAlphabeticName(command.firstName())
                || !InputValidator.isAlphabeticName(command.lastName())
                || command.firstName().length() < 3
                || command.lastName().length() < 3
                || !InputValidator.isValidMobile(command.mobile())
                || !InputValidator.isValidEmail(command.email())
                || command.location() == null || command.location().isBlank()) {
            throw new IllegalArgumentException("CIBIL onboarding details are invalid");
        }
        validatePanAndLoanType(command.pan(), command.loanType());
    }

    private void validatePanAndLoanType(String pan, LoanType loanType) {
        if (!InputValidator.isValidPan(pan)) {
            throw new IllegalArgumentException("Invalid PAN format. Please enter a valid PAN.");
        }
        if (loanType == null) {
            throw new IllegalArgumentException("Invalid loan type selected.");
        }
    }

    private String generatedUsername(CibilOnboardingCommand command) {
        String prefix = command.firstName().substring(0, 3)
                + command.lastName().substring(0, 3)
                + command.mobile().substring(6);
        String base = prefix.toLowerCase(Locale.ROOT);
        String candidate = base;
        int suffix = 2;
        while (users.existsByUsername(candidate)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private char[] generateTemporaryPassword() {
        char[] password = new char[10];
        password[0] = randomFrom("ABCDEFGHJKLMNPQRSTUVWXYZ");
        password[1] = randomFrom("abcdefghijkmnopqrstuvwxyz");
        password[2] = randomFrom("23456789");
        password[3] = randomFrom("!@#$%&*");
        for (int index = 4; index < password.length; index++) {
            password[index] = randomFrom(PASSWORD_ALPHABET);
        }
        for (int index = password.length - 1; index > 0; index--) {
            int swapIndex = RANDOM.nextInt(index + 1);
            char current = password[index];
            password[index] = password[swapIndex];
            password[swapIndex] = current;
        }
        return password;
    }

    private char randomFrom(String alphabet) {
        return alphabet.charAt(RANDOM.nextInt(alphabet.length()));
    }

    private User withCibil(User user, String pan, int score) {
        return new User(user.id(), user.firstName(), user.lastName(), user.mobile(), user.email(), user.location(),
                user.username(), user.passwordHash(), user.mustChangePassword(), user.role(), user.status(),
                user.failedAttempts(), pan, score, user.createdAt(), user.lastModifiedAt());
    }

    private void requireActiveUser(User user) {
        if (user == null || user.status() != AccountStatus.ACTIVE || user.role() != UserRole.ROLE_USER) {
            throw new SecurityException("Active user account required");
        }
    }
}