package com.loanmanagement.notification;

import java.util.Properties;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

public final class SmtpEmailSender implements EmailSender {
    private final String host;
    private final String port;
    private final String username;
    private final String password;
    private final String fromAddress;

    public SmtpEmailSender() {
        host = System.getenv("SMTP_HOST");
        port = System.getenv().getOrDefault("SMTP_PORT", "587");
        username = System.getenv("SMTP_USER");
        password = System.getenv("SMTP_PASSWORD");
        fromAddress = System.getenv("SMTP_FROM");
    }

    @Override
    public void send(String recipient, String subject, String body) {
        if (isBlank(host) || isBlank(fromAddress)) {
            throw new EmailDeliveryException("SMTP_HOST and SMTP_FROM must be configured to send email");
        }

        boolean authenticated = !isBlank(username);
        Properties properties = new Properties();
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", port);
        properties.put("mail.smtp.auth", Boolean.toString(authenticated));
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        Session session = Session.getInstance(properties, authenticated
                ? new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password == null ? "" : password);
                    }
                }
                : null);

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromAddress));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient, true));
            message.setSubject(subject, "UTF-8");
            message.setText(body, "UTF-8");
            Transport.send(message);
        } catch (MessagingException exception) {
            throw new EmailDeliveryException("Email delivery failed", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}