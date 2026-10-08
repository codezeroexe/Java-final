package com.loanmanagement.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.loanmanagement.dao.AdminUserRepository;
import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;
import com.loanmanagement.notification.EmailDeliveryException;
import com.loanmanagement.notification.EmailSender;
import com.loanmanagement.validation.InputValidator;

public final class AdminUserService {
    private final AdminUserRepository users;
    private final EmailSender emailSender;

    public AdminUserService(AdminUserRepository users, EmailSender emailSender) {
        this.users = users;
        this.emailSender = emailSender;
    }

    public List<User> searchUsers(User actor, UserSearchField field, String query) {
        requireAdmin(actor);
        if (field == null || query == null || query.isBlank()) {
            throw new IllegalArgumentException("Search field and query are required");
        }
        String normalized = field == UserSearchField.STATUS
                ? query.trim().toUpperCase(Locale.ROOT)
                : query.trim();
        if (field == UserSearchField.STATUS
                && !normalized.equals("ACTIVE") && !normalized.equals("INACTIVE")) {
            throw new IllegalArgumentException("Status must be ACTIVE or INACTIVE");
        }
        return users.searchUsers(field, normalized);
    }

    public Optional<User> viewUser(User actor, long userId) {
        requireAdmin(actor);
        return users.findActiveUser(userId);
    }

    public AdminActionResult updateUser(User actor, long userId, UserProfileUpdate update) {
        requireAdmin(actor);
        validateUpdate(update);
        User target = requireTarget(userId);
        users.updateUser(actor.id(), userId, new UserProfileUpdate(
                update.firstName().trim(), update.lastName().trim(), update.mobile().trim(),
                update.email().trim().toLowerCase(Locale.ROOT), blankToNull(update.location())));
        return notify(target.email(), target.firstName(),
                "Your account details have been updated. If you did not request this change, please contact support.");
    }

    public AdminActionResult setUserStatus(User actor, long userId, AccountStatus status) {
        requireAdmin(actor);
        if (status == null) {
            throw new IllegalArgumentException("Account status is required");
        }
        User target = requireTarget(userId);
        users.updateStatus(actor.id(), userId, status);
        String body = status == AccountStatus.ACTIVE
                ? "Your account has been successfully activated by the admin. You can now log in to the system."
                : "Your account has been deactivated. Please contact support for more information.";
        return notify(target.email(), target.firstName(), body);
    }

    public AdminActionResult deleteUser(User actor, long userId, boolean confirmed) {
        requireAdmin(actor);
        if (!confirmed) {
            throw new IllegalArgumentException("Deletion was not confirmed");
        }
        if (actor.id() == userId) {
            throw new IllegalArgumentException("Admins cannot delete their own account");
        }
        User target = requireTarget(userId);
        users.anonymizeUser(actor.id(), userId);
        return notify(target.email(), target.firstName(),
                "Your account has been deleted from the system. If this was a mistake, please contact support immediately.");
    }

    private AdminActionResult notify(String email, String firstName, String body) {
        try {
            emailSender.send(email, "Account Update Notification", "Dear " + firstName + ", " + body);
            return new AdminActionResult("Account action completed and email sent.", true);
        } catch (EmailDeliveryException exception) {
            return new AdminActionResult("Account action completed, but email delivery failed.", false);
        }
    }

    private User requireTarget(long userId) {
        return users.findActiveUser(userId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found"));
    }

    private void requireAdmin(User actor) {
        if (actor == null || actor.status() != AccountStatus.ACTIVE || actor.role() != UserRole.ROLE_ADMIN) {
            throw new SecurityException("Admin access required");
        }
    }

    private void validateUpdate(UserProfileUpdate update) {
        if (update == null || !InputValidator.isValidName(update.firstName())
                || !InputValidator.isValidName(update.lastName())
                || !InputValidator.isValidMobile(update.mobile())
                || !InputValidator.isValidEmail(update.email())) {
            throw new IllegalArgumentException("User details are invalid");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}