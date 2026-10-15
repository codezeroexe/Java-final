package com.loanmanagement.testsupport;

import java.util.ArrayList;
import java.util.List;

import com.loanmanagement.dao.NotificationRepository;

/** In-memory outbox for tests. Keeps every queued notice and whether it was sent. */
public final class InMemoryNotificationRepository implements NotificationRepository {
    private static final class Row {
        final long id;
        final String recipient;
        final String subject;
        final String body;
        boolean sent;
        String lastError;

        Row(long id, String recipient, String subject, String body, String lastError) {
            this.id = id;
            this.recipient = recipient;
            this.subject = subject;
            this.body = body;
            this.lastError = lastError;
        }
    }

    private final List<Row> rows = new ArrayList<>();

    @Override
    public void enqueue(String recipient, String subject, String body, String error) {
        rows.add(new Row(rows.size() + 1L, recipient, subject, body, error));
    }

    @Override
    public List<Pending> listPending(int limit) {
        return rows.stream().filter(row -> !row.sent).limit(limit)
                .map(row -> new Pending(row.id, row.recipient, row.subject, row.body)).toList();
    }

    @Override
    public void markSent(long id) {
        row(id).sent = true;
        row(id).lastError = null;
    }

    @Override
    public void recordFailure(long id, String error) {
        row(id).lastError = error;
    }

    public int pendingCount() {
        return (int) rows.stream().filter(row -> !row.sent).count();
    }

    public String lastError(long id) {
        return row(id).lastError;
    }

    private Row row(long id) {
        return rows.stream().filter(row -> row.id == id).findFirst().orElseThrow();
    }
}
