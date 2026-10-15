package com.loanmanagement.notification;

import com.loanmanagement.dao.NotificationRepository;

/**
 * Sends through a delegate and queues the message when delivery fails, so an admin can resend it later.
 * The failure is still thrown, so the calling service reports it exactly as before.
 *
 * Use only for account and loan notices. Messages that carry secrets (password reset tokens, temporary
 * passwords) must not be queued, because the queue is stored in the database.
 */
public final class OutboxEmailSender implements EmailSender {
    private final EmailSender delegate;
    private final NotificationRepository outbox;

    public OutboxEmailSender(EmailSender delegate, NotificationRepository outbox) {
        this.delegate = delegate;
        this.outbox = outbox;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        try {
            delegate.send(recipient, subject, body);
        } catch (EmailDeliveryException exception) {
            outbox.enqueue(recipient, subject, body, exception.getMessage());
            throw exception;
        }
    }
}
