package com.loanmanagement.tui;

import java.sql.SQLException;

import com.loanmanagement.dao.JdbcUserRepository;
import com.loanmanagement.db.ConnectionFactory;
import com.loanmanagement.db.SchemaMigrator;
import com.loanmanagement.dao.JdbcLoanRepository;
import com.loanmanagement.notification.SmtpEmailSender;
import com.loanmanagement.security.PasswordHasher;
import com.loanmanagement.service.AdminBootstrapper;
import com.loanmanagement.service.AuthenticationService;
import com.loanmanagement.service.CibilService;
import com.loanmanagement.service.EmiCalculator;
import com.loanmanagement.service.LoanService;
import com.loanmanagement.service.InterestRatePolicy;
import com.loanmanagement.service.PasswordResetService;
import com.loanmanagement.tui.account.AccountScreens;
import com.loanmanagement.tui.account.AppContext;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.screen.TuiApplication;

/**
 * Full-screen entry point. Database setup runs before the terminal is taken over, so configuration
 * errors print normally. Wiring mirrors {@code Main}.
 */
public final class TuiLauncher {
    private TuiLauncher() {
    }

    public static void main(String[] args) throws Exception {
        ConnectionFactory connections = ConnectionFactory.fromEnvironment();
        try {
            new SchemaMigrator(connections).migrate();
        } catch (SQLException exception) {
            System.err.println("Database unavailable or migration failed. Check DB_URL, DB_USER, and MySQL service.");
            return;
        }
        JdbcUserRepository users = new JdbcUserRepository(connections);
        PasswordHasher passwordHasher = new PasswordHasher();
        new AdminBootstrapper(users, users, passwordHasher).bootstrapFromEnvironment();
        AuthenticationService authentication = new AuthenticationService(users, passwordHasher);
        SmtpEmailSender emailSender = new SmtpEmailSender();
        InterestRatePolicy interestRates = new InterestRatePolicy();
        AppContext context = new AppContext(authentication,
                new PasswordResetService(users, passwordHasher, emailSender),
                new CibilService(users, users, authentication, interestRates, emailSender),
                new LoanService(new JdbcLoanRepository(connections), interestRates, new EmiCalculator(), emailSender));

        try (TerminalRenderer renderer = TerminalRenderer.openTerminal()) {
            new TuiApplication(renderer).run(AccountScreens.root(context));
        }
    }
}
