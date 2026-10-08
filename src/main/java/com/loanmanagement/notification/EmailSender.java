package com.loanmanagement.notification;

public interface EmailSender {
    void send(String recipient, String subject, String body);
}