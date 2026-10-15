package com.loanmanagement.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.loanmanagement.testsupport.InMemoryNotificationRepository;

class OutboxEmailSenderTest {

    @Test
    void failedDeliveryIsQueuedAndStillReported() {
        InMemoryNotificationRepository outbox = new InMemoryNotificationRepository();
        EmailSender failing = (recipient, subject, body) -> {
            throw new EmailDeliveryException("SMTP down");
        };
        EmailSender sender = new OutboxEmailSender(failing, outbox);

        assertThrows(EmailDeliveryException.class,
                () -> sender.send("asha@example.com", "Subject", "Body"));
        assertEquals(1, outbox.pendingCount());
        assertEquals("SMTP down", outbox.lastError(1));
    }

    @Test
    void successfulDeliveryIsNotQueued() {
        InMemoryNotificationRepository outbox = new InMemoryNotificationRepository();
        EmailSender sender = new OutboxEmailSender((recipient, subject, body) -> { }, outbox);

        sender.send("asha@example.com", "Subject", "Body");
        assertEquals(0, outbox.pendingCount());
    }
}
