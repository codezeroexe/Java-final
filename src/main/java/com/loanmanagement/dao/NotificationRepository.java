package com.loanmanagement.dao;

import java.util.List;

/** Mail that could not be delivered, kept for a later resend. Only account and loan notices are stored. */
public interface NotificationRepository {
    /** A queued notice with its database id. */
    record Pending(long id, String recipient, String subject, String body) {
    }

    void enqueue(String recipient, String subject, String body, String error);

    List<Pending> listPending(int limit);

    void markSent(long id);

    void recordFailure(long id, String error);
}
