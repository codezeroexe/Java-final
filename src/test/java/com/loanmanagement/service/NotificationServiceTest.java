package com.loanmanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;
import com.loanmanagement.notification.EmailDeliveryException;
import com.loanmanagement.testsupport.InMemoryNotificationRepository;

class NotificationServiceTest {

    private static User user(UserRole role) {
        Instant now = Instant.now();
        return new User(1, "Asha", "Patel", "9876543210", "asha@example.com", null, "asha", "hash", false, role,
                AccountStatus.ACTIVE, 0, null, null, now, now);
    }

    @Test
    void resendSendsWhatNowSucceedsAndKeepsWhatStillFails() {
        InMemoryNotificationRepository outbox = new InMemoryNotificationRepository();
        outbox.enqueue("a@example.com", "One", "body one", "SMTP down");
        outbox.enqueue("b@example.com", "Two", "body two", "SMTP down");
        List<String> delivered = new ArrayList<>();
        var service = new NotificationService(outbox, (recipient, subject, body) -> {
            if (recipient.equals("b@example.com")) {
                throw new EmailDeliveryException("still down");
            }
            delivered.add(recipient);
        });

        NotificationService.ResendResult result = service.resendPending(user(UserRole.ROLE_ADMIN));

        assertEquals(new NotificationService.ResendResult(1, 1), result);
        assertEquals(List.of("a@example.com"), delivered);
        assertEquals(1, outbox.pendingCount());
        assertEquals("still down", outbox.lastError(2));
    }

    @Test
    void resendRequiresAdmin() {
        var service = new NotificationService(new InMemoryNotificationRepository(), (r, s, b) -> { });
        assertThrows(SecurityException.class, () -> service.resendPending(user(UserRole.ROLE_USER)));
    }
}
