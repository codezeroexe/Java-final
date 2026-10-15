package com.loanmanagement.service;

import java.util.List;

import com.loanmanagement.dao.NotificationRepository;
import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;
import com.loanmanagement.notification.EmailDeliveryException;
import com.loanmanagement.notification.EmailSender;

/** Admin action: retry mail that failed to deliver earlier. */
public final class NotificationService {
    public record ResendResult(int sent, int failed) {
    }

    private static final int BATCH_LIMIT = 50;

    private final NotificationRepository outbox;
    private final EmailSender emailSender;

    /** {@code emailSender} is the plain sender, not the queueing one, so a resend failure is not queued twice. */
    public NotificationService(NotificationRepository outbox, EmailSender emailSender) {
        this.outbox = outbox;
        this.emailSender = emailSender;
    }

    public ResendResult resendPending(User admin) {
        requireAdmin(admin);
        int sent = 0;
        int failed = 0;
        List<NotificationRepository.Pending> pending = outbox.listPending(BATCH_LIMIT);
        for (NotificationRepository.Pending notice : pending) {
            try {
                emailSender.send(notice.recipient(), notice.subject(), notice.body());
                outbox.markSent(notice.id());
                sent++;
            } catch (EmailDeliveryException exception) {
                outbox.recordFailure(notice.id(), exception.getMessage());
                failed++;
            }
        }
        return new ResendResult(sent, failed);
    }

    private void requireAdmin(User user) {
        if (user == null || user.status() != AccountStatus.ACTIVE || user.role() != UserRole.ROLE_ADMIN) {
            throw new SecurityException("Admin access required");
        }
    }
}
