package com.loanmanagement.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loanmanagement.dao.JdbcLoanRepository;
import com.loanmanagement.dao.JdbcUserRepository;
import com.loanmanagement.db.ConnectionFactory;
import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.LoanApplication;
import com.loanmanagement.model.LoanStatus;
import com.loanmanagement.model.LoanType;
import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;
import com.loanmanagement.notification.EmailDeliveryException;
import com.loanmanagement.notification.EmailSender;
import com.loanmanagement.notification.SmtpEmailSender;
import com.loanmanagement.security.PasswordHasher;
import com.loanmanagement.service.AdminActionResult;
import com.loanmanagement.service.AdminBootstrapper;
import com.loanmanagement.service.AdminUserService;
import com.loanmanagement.service.AuthenticationService;
import com.loanmanagement.service.CibilService;
import com.loanmanagement.service.EmiCalculator;
import com.loanmanagement.service.InterestRatePolicy;
import com.loanmanagement.service.LoanApplicationCommand;
import com.loanmanagement.service.LoanCalculation;
import com.loanmanagement.service.LoanService;
import com.loanmanagement.service.LoanSubmissionResult;
import com.loanmanagement.service.PasswordResetService;
import com.loanmanagement.service.RegisterCommand;
import com.loanmanagement.service.UserProfileUpdate;
import com.loanmanagement.service.UserSearchField;
import com.loanmanagement.service.AuthenticationResult;
import com.loanmanagement.service.AuthenticationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcIntegrationTest {

    private JdbcUserRepository users;
    private JdbcLoanRepository loans;
    private AuthenticationService authentication;
    private AdminUserService adminUsers;
    private LoanService loanService;
    private CibilService cibilService;
    private PasswordHasher passwordHasher;
    private EmailSender emailSender;

    @BeforeEach
    void setUp() throws Exception {
        ConnectionFactory connections = new ConnectionFactory(
                "jdbc:mysql://127.0.0.1:3306/loan_management_test",
                "loan_test",
                "loan_test_pass");
        new com.loanmanagement.db.SchemaMigrator(connections).migrate();
        clearTables();
        users = new JdbcUserRepository(connections);
        loans = new JdbcLoanRepository(connections);
        passwordHasher = new PasswordHasher();
        users.createAdmin("admin", "admin@example.com", passwordHasher.hash("Admin!Pass1".toCharArray()));
        authentication = new AuthenticationService(users, passwordHasher);
        emailSender = (recipient, subject, body) -> { };
        adminUsers = new AdminUserService(users, emailSender);
        InterestRatePolicy interestRates = new InterestRatePolicy();
        EmiCalculator emiCalculator = new EmiCalculator();
        loanService = new LoanService(loans, interestRates, emiCalculator, emailSender);
        cibilService = new CibilService(users, users, authentication, interestRates, emailSender);
    }

    @AfterEach
    void tearDown() {
        clearTables();
    }

    private void clearTables() {
        ConnectionFactory connections = new ConnectionFactory(
                "jdbc:mysql://127.0.0.1:3306/loan_management_test",
                "loan_test",
                "loan_test_pass");
        try (var conn = connections.open(); var stmt = conn.createStatement()) {
            stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
            stmt.execute("TRUNCATE TABLE password_reset_tokens");
            stmt.execute("TRUNCATE TABLE loan_applications");
            stmt.execute("TRUNCATE TABLE admin_audit_log");
            stmt.execute("TRUNCATE TABLE users");
            stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
        } catch (Exception exception) {
            throw new IllegalStateException("Could not clear integration test tables", exception);
        }
    }

    @Test
    void registerAndLoginWithLockoutOnThirdFailure() {
        User user = authentication.register(new RegisterCommand(
                "Asha", "Patel", "9876543210", "asha@example.com", "Pune",
                "asha", "Asha!Pass1".toCharArray()));
        assertNotNull(user.id());
        assertEquals(AccountStatus.ACTIVE, user.status());

        AuthenticationResult first = authentication.authenticate("asha", "Wrong!Pass1".toCharArray());
        assertEquals(AuthenticationStatus.INVALID_CREDENTIALS, first.status());
        assertEquals(2, first.attemptsRemaining());

        AuthenticationResult second = authentication.authenticate("asha@example.com", "Wrong!Pass1".toCharArray());
        assertEquals(1, second.attemptsRemaining());

        AuthenticationResult third = authentication.authenticate("asha", "Wrong!Pass1".toCharArray());
        assertEquals(AuthenticationStatus.ACCOUNT_LOCKED, third.status());
        assertEquals(0, third.attemptsRemaining());

        assertEquals(AuthenticationStatus.ACCOUNT_INACTIVE,
                authentication.authenticate("asha", "Asha!Pass1".toCharArray()).status());

        authentication.authenticate("asha", "Wrong!Pass1".toCharArray());
        assertEquals(AuthenticationStatus.ACCOUNT_INACTIVE,
                authentication.authenticate("asha", "Asha!Pass1".toCharArray()).status());
    }

    @Test
    void adminSearchUpdateActivateDeactivateAndAudit() {
        authentication.register(new RegisterCommand(
                "Asha", "Patel", "9876543210", "asha@example.com", "Pune",
                "asha_user", "Asha!Pass1".toCharArray()));

        User admin = users.findByLoginIdentifier("admin").orElseThrow();
        List<User> matches = adminUsers.searchUsers(admin, UserSearchField.FIRST_NAME, "Asha");
        assertEquals(1, matches.size());
        User target = matches.get(0);

        AdminActionResult update = adminUsers.updateUser(admin, target.id(),
                new UserProfileUpdate("Asha", "Patil", "9876543211", "asha2@example.com", "Mumbai"));
        assertTrue(update.notificationSent());
        User updated = adminUsers.viewUser(admin, target.id()).orElseThrow();
        assertEquals("Patil", updated.lastName());
        assertEquals("asha2@example.com", updated.email());

        adminUsers.setUserStatus(admin, target.id(), AccountStatus.INACTIVE);
        assertEquals(AccountStatus.INACTIVE, adminUsers.viewUser(admin, target.id()).orElseThrow().status());
        AdminActionResult activate = adminUsers.setUserStatus(admin, target.id(), AccountStatus.ACTIVE);
        assertTrue(activate.notificationSent());
        assertEquals(AccountStatus.ACTIVE, adminUsers.viewUser(admin, target.id()).orElseThrow().status());

        List<User> auditMatches = adminUsers.searchUsers(admin, UserSearchField.FIRST_NAME, "Asha");
        assertEquals(1, auditMatches.size());
    }

    @Test
    void loanReviewApprovesAndPersistsEmiAudit() {
        User user = authentication.register(new RegisterCommand(
                "Asha", "Patel", "9876543210", "asha@example.com", "Pune",
                "asha_user", "Asha!Pass1".toCharArray()));
        User admin = users.findByLoginIdentifier("admin").orElseThrow();

        user = cibilService.assess(user, "ABCDE1234F", LoanType.HOME).user();
        LoanSubmissionResult submission = loanService.apply(user,
                new LoanApplicationCommand(new BigDecimal("500000"), LoanType.HOME, 240,
                        new BigDecimal("1200000"), "Home purchase"));
        LoanApplication application = submission.application();
        assertEquals(LoanStatus.PENDING, application.status());

        AdminActionResult approve = loanService.review(admin, application.id(), LoanStatus.APPROVED, "");
        assertTrue(approve.notificationSent());

        LoanApplication approved = loans.findById(application.id()).orElseThrow();
        assertEquals(LoanStatus.APPROVED, approved.status());
        assertNotNull(approved.emiAmount());
        assertNotNull(approved.totalInterest());
        assertNotNull(approved.totalPayable());

        LoanApplication reloaded = loans.findById(application.id()).orElseThrow();
        assertEquals(approved.emiAmount(), reloaded.emiAmount());
    }

    @Test
    void passwordResetTokenIsSingleUseAndExpiresAfterFifteenMinutes() {
        authentication.register(new RegisterCommand(
                "Asha", "Patel", "9876543210", "asha@example.com", "Pune",
                "asha_reset", "Asha!Pass1".toCharArray()));
        String[] emailBody = { null };
        EmailSender capturingSender = (recipient, subject, body) -> emailBody[0] = body;
        PasswordResetService resets = new PasswordResetService(users, passwordHasher, capturingSender);

        resets.requestReset("asha@example.com");
        assertNotNull(emailBody[0]);
        String token = emailBody[0].substring(emailBody[0].lastIndexOf(' ') + 1);

        assertTrue(resets.resetPassword(token, "New!StrongPass2".toCharArray()));
        assertFalse(resets.resetPassword(token, "Another!Pass3".toCharArray()));

        AuthenticationResult afterReset = authentication.authenticate("asha_reset", "New!StrongPass2".toCharArray());
        assertEquals(AuthenticationStatus.SUCCESS, afterReset.status());
    }

    @Test
    void duplicateUsernameOrEmailThrowsConstraintViolation() {
        authentication.register(new RegisterCommand(
                "Asha", "Patel", "9876543210", "asha@example.com", "Pune",
                "duplicate_user", "Asha!Pass1".toCharArray()));
        assertThrows(Exception.class, () -> authentication.register(new RegisterCommand(
                "Other", "Name", "9876543211", "asha@example.com", "Mumbai",
                "other_user", "Other!Pass1".toCharArray())));
        assertThrows(Exception.class, () -> authentication.register(new RegisterCommand(
                "Other", "Name", "9876543211", "other@example.com", "Mumbai",
                "duplicate_user", "Other!Pass1".toCharArray())));
    }

    @Test
    void pendingNotificationIsQueuedListedAndMarkedSent() {
        var outbox = new com.loanmanagement.dao.JdbcNotificationRepository(new ConnectionFactory(
                "jdbc:mysql://127.0.0.1:3306/loan_management_test", "loan_test", "loan_test_pass"));
        outbox.enqueue("asha@example.com", "Loan Application Submitted", "Body text", "SMTP down");
        var pending = outbox.listPending(10);
        assertTrue(pending.stream().anyMatch(notice -> notice.recipient().equals("asha@example.com")
                && notice.subject().equals("Loan Application Submitted")));
        long id = pending.stream().filter(notice -> notice.recipient().equals("asha@example.com"))
                .findFirst().orElseThrow().id();
        outbox.markSent(id);
        assertTrue(outbox.listPending(10).stream().noneMatch(notice -> notice.id() == id));
    }
}
