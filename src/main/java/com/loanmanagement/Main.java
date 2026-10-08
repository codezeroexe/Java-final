package com.loanmanagement;

import java.sql.SQLException;
import java.util.Scanner;

import com.loanmanagement.cli.ConsoleApplication;
import com.loanmanagement.dao.JdbcLoanRepository;
import com.loanmanagement.dao.JdbcUserRepository;
import com.loanmanagement.db.ConnectionFactory;
import com.loanmanagement.db.SchemaMigrator;
import com.loanmanagement.notification.SmtpEmailSender;
import com.loanmanagement.security.PasswordHasher;
import com.loanmanagement.service.AdminBootstrapper;
import com.loanmanagement.service.AdminUserService;
import com.loanmanagement.service.AuthenticationService;
import com.loanmanagement.service.CibilService;
import com.loanmanagement.service.EmiCalculator;
import com.loanmanagement.service.InterestRatePolicy;
import com.loanmanagement.service.LoanService;
import com.loanmanagement.service.PasswordResetService;

public final class Main {
    private Main() {
    }

    /** The plain console is used when {@code --plain} is passed or when input or output is not a terminal. */
    static boolean isPlainRequested(String[] args) {
        for (String arg : args) {
            if ("--plain".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) {
        if (!isPlainRequested(args) && com.loanmanagement.tui.TuiLauncher.isInteractive()) {
            try {
                com.loanmanagement.tui.TuiLauncher.main(args);
            } catch (Exception exception) {
                System.err.println(exception.getMessage());
            }
            return;
        }
        try {
            ConnectionFactory connections = ConnectionFactory.fromEnvironment();
            new SchemaMigrator(connections).migrate();
            JdbcUserRepository users = new JdbcUserRepository(connections);
            PasswordHasher passwordHasher = new PasswordHasher();
            if (new AdminBootstrapper(users, users, passwordHasher).bootstrapFromEnvironment()) {
                System.out.println("Initial admin account created from environment configuration.");
            }
            AuthenticationService authentication = new AuthenticationService(users, passwordHasher);
            SmtpEmailSender emailSender = new SmtpEmailSender();
            AdminUserService adminUsers = new AdminUserService(users, emailSender);
            JdbcLoanRepository loanRepository = new JdbcLoanRepository(connections);
            InterestRatePolicy interestRates = new InterestRatePolicy();
            EmiCalculator emiCalculator = new EmiCalculator();
            LoanService loans = new LoanService(loanRepository, interestRates, emiCalculator, emailSender);
            CibilService cibil = new CibilService(users, users, authentication, interestRates, emailSender);
            PasswordResetService passwordResets = new PasswordResetService(users, passwordHasher, emailSender);
            try (Scanner scanner = new Scanner(System.in)) {
                new ConsoleApplication(scanner, authentication, adminUsers, loans, cibil, passwordResets).run();
            }
        } catch (RuntimeException exception) {
            System.err.println(exception.getMessage());
        } catch (SQLException exception) {
            System.err.println("Database unavailable or migration failed. Check DB_URL, DB_USER, and MySQL service.");
        }
    }
}