package com.loanmanagement.cli;

import java.util.Arrays;
import java.util.Scanner;

import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;
import com.loanmanagement.service.AdminUserService;
import com.loanmanagement.service.AuthenticationResult;
import com.loanmanagement.service.AuthenticationService;
import com.loanmanagement.service.CibilAssessmentResult;
import com.loanmanagement.service.CibilOnboardingCommand;
import com.loanmanagement.service.CibilOnboardingResult;
import com.loanmanagement.service.CibilService;
import com.loanmanagement.service.LoanApplicationCommand;
import com.loanmanagement.service.LoanService;
import com.loanmanagement.service.LoanSubmissionResult;
import com.loanmanagement.service.PasswordResetService;
import com.loanmanagement.service.RegisterCommand;
import com.loanmanagement.model.LoanApplication;
import com.loanmanagement.model.LoanType;
import java.math.BigDecimal;

public final class ConsoleApplication {
    private final Scanner scanner;
    private final AuthenticationService authentication;
    private final AdminUserService adminUsers;
    private final LoanService loans;
    private final CibilService cibil;
    private final PasswordResetService passwordResets;

    public ConsoleApplication(
            Scanner scanner,
            AuthenticationService authentication,
            AdminUserService adminUsers,
            LoanService loans,
            CibilService cibil,
            PasswordResetService passwordResets) {
        this.scanner = scanner;
        this.authentication = authentication;
        this.adminUsers = adminUsers;
        this.loans = loans;
        this.cibil = cibil;
        this.passwordResets = passwordResets;
    }

    public void run() {
        boolean running = true;
        while (running && scanner.hasNextLine()) {
            System.out.println("\nLoan Management System");
            System.out.println("1. Register");
            System.out.println("2. Login");
            System.out.println("3. CIBIL onboarding");
            System.out.println("4. Forgot password");
            System.out.println("5. Exit");
            switch (readLine("Select option: ")) {
                case "1" -> register();
                case "2" -> login();
                case "3" -> cibilOnboarding();
                case "4" -> resetPassword();
                case "5" -> running = false;
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private void register() {
        System.out.println("\nRegister");
        String firstName = readLine("First name: ");
        String lastName = readLine("Last name: ");
        String mobile = readLine("Mobile (10 digits): ");
        String email = readLine("Email: ");
        String location = readLine("Location (optional): ");
        String username = readLine("Username: ");
        char[] password = readPassword("Password: ");
        try {
            User user = authentication.register(new RegisterCommand(
                    firstName, lastName, mobile, email, location, username, password));
            System.out.println("Registration successful for " + user.username() + ".");
        } catch (IllegalArgumentException exception) {
            System.out.println(exception.getMessage());
        } catch (RuntimeException exception) {
            System.out.println("Could not register account. Check whether username or email is already in use.");
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void login() {
        System.out.println("\nLogin");
        String identifier = readLine("Email or username: ");
        char[] password = readPassword("Password: ");
        AuthenticationResult result;
        try {
            result = authentication.authenticate(identifier, password);
        } catch (RuntimeException exception) {
            System.out.println("Login service is unavailable. Try again later.");
            return;
        } finally {
            Arrays.fill(password, '\0');
        }

        switch (result.status()) {
            case SUCCESS -> showAuthenticatedMenu(result.user().orElseThrow());
            case INVALID_CREDENTIALS -> {
                System.out.println("Invalid credentials.");
                if (result.attemptsRemaining() > 0) {
                    System.out.println(result.attemptsRemaining() + " attempts remain before account lockout.");
                }
            }
            case ACCOUNT_LOCKED -> System.out.println("Account locked. Contact support or an administrator.");
            case ACCOUNT_INACTIVE -> System.out.println("Account is inactive. Contact an administrator.");
        }
    }

    private void resetPassword() {
        String identifier = readLine("Email or username: ");
        passwordResets.requestReset(identifier);
        System.out.println("If an active account matches, a reset token was sent to its registered email.");
        String token = readLine("Reset token (press Enter to cancel): ");
        if (token.isBlank()) {
            return;
        }
        char[] newPassword = readPassword("New password: ");
        try {
            if (passwordResets.resetPassword(token, newPassword)) {
                System.out.println("Password reset successful.");
            } else {
                System.out.println("Reset token is invalid, expired, or already used.");
            }
        } catch (IllegalArgumentException exception) {
            System.out.println(exception.getMessage());
        } finally {
            Arrays.fill(newPassword, '\0');
        }
    }

    private void showAuthenticatedMenu(User user) {
        if (user.mustChangePassword() && !rotateTemporaryPassword(user)) {
            return;
        }
        System.out.println("Welcome, " + user.firstName() + " " + user.lastName());
        if (user.role() == UserRole.ROLE_ADMIN) {
            new AdminConsoleMenu(scanner, adminUsers, loans).run(user);
            return;
        }

        while (scanner.hasNextLine()) {
            System.out.println("1. Apply for loan");
            System.out.println("2. View my loans");
            System.out.println("3. View loan details");
            System.out.println("4. PAN and CIBIL assessment");
            System.out.println("5. My profile");
            System.out.println("6. Logout");
            switch (readLine("Select option: ")) {
                case "1" -> applyForLoan(user);
                case "2" -> printLoans(loans.findMyLoans(user));
                case "3" -> viewLoanDetails(user, loans.findMyLoans(user));
                case "4" -> user = assessCibil(user);
                case "5" -> printProfile(user);
                case "6" -> { return; }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private void cibilOnboarding() {
        try {
            CibilOnboardingCommand command = new CibilOnboardingCommand(
                    readLine("First name: "), readLine("Last name: "), readLine("Mobile (10 digits): "),
                    readLine("Email: "), readLine("Location: "), readLine("PAN: "), readLoanType());
            CibilOnboardingResult result = cibil.onboard(command);
            System.out.printf("CIBIL score: %d%nInterest rate: %s%%%nUsername: %s%n",
                    result.user().cibilScore(), result.interestRate(), result.user().username());
            if (!result.credentialsEmailSent()) {
                System.out.println("Credentials email failed. Temporary password: " + result.recoveryPassword());
            }
            showAuthenticatedMenu(result.user());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.out.println(exception.getMessage());
        }
    }

    private User assessCibil(User user) {
        try {
            CibilAssessmentResult result = cibil.assess(user, readLine("PAN: "), readLoanType());
            System.out.printf("CIBIL score: %d%nInterest rate: %s%%%n",
                    result.user().cibilScore(), result.interestRate());
            return result.user();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.out.println(exception.getMessage());
            return user;
        }
    }

    private boolean rotateTemporaryPassword(User user) {
        while (scanner.hasNextLine()) {
            char[] newPassword = readPassword("Temporary password must be changed. New password: ");
            try {
                authentication.changePassword(user, newPassword);
                return true;
            } catch (IllegalArgumentException | IllegalStateException exception) {
                System.out.println(exception.getMessage());
            } finally {
                Arrays.fill(newPassword, '\0');
            }
        }
        return false;
    }

    private void applyForLoan(User user) {
        BigDecimal amount = readAmount("Loan amount: ");
        LoanType type = readLoanType();
        int months = (int) readNumber("Tenure in months: ");
        BigDecimal income = readAmount("Annual income: ");
        String purpose = readLine("Purpose: ");
        if (purpose.isBlank()) {
            System.out.println("Purpose is required.");
            return;
        }
        try {
            LoanSubmissionResult result = loans.apply(user,
                    new LoanApplicationCommand(amount, type, months, income, purpose));
            System.out.println("Application " + result.application().id() + " submitted as Pending.");
            if (!result.notificationSent()) {
                System.out.println("Application saved, but confirmation email was not sent.");
            }
        } catch (IllegalArgumentException exception) {
            System.out.println("Enter valid loan details. " + exception.getMessage());
        } catch (IllegalStateException exception) {
            System.out.println(exception.getMessage());
        }
    }

    private LoanType readLoanType() {
        while (scanner.hasNextLine()) {
            System.out.println("1. Home Loan");
            System.out.println("2. Personal Loan");
            System.out.println("3. Education Loan");
            System.out.println("4. Vehicle Loan");
            LoanType type = switch (readLine("Loan type: ")) {
                case "1" -> LoanType.HOME;
                case "2" -> LoanType.PERSONAL;
                case "3" -> LoanType.EDUCATION;
                case "4" -> LoanType.VEHICLE;
                default -> null;
            };
            if (type != null) {
                return type;
            }
            System.out.println("Invalid loan type selected.");
        }
        throw new IllegalStateException("Input closed.");
    }

    private void viewLoanDetails(User user, java.util.List<LoanApplication> applications) {
        if (applications.isEmpty()) {
            System.out.println("No loan applications found.");
            return;
        }
        printLoans(applications);
        long loanId = readNumber("Loan ID to inspect: ");
        applications.stream()
                .filter(loan -> loan.id() == loanId)
                .findFirst()
                .ifPresentOrElse(ConsoleApplication::printLoanDetail,
                        () -> System.out.println("No loan application with ID " + loanId + " belongs to you."));
    }

    private void printProfile(User user) {
        System.out.println("Username: " + user.username());
        System.out.println("Name: " + user.firstName() + " " + user.lastName());
        System.out.println("Email: " + user.email());
        System.out.println("Mobile: " + user.mobile());
        System.out.println("Location: " + (user.location() == null ? "Not provided" : user.location()));
        System.out.println("PAN: " + (user.pan() == null ? "Not assessed" : user.pan()));
        System.out.println("CIBIL score: " + (user.cibilScore() == null
                ? "Not assessed" : user.cibilScore()));
        System.out.println("Status: " + user.status());
    }

    static void printLoans(java.util.List<LoanApplication> applications) {
        if (applications.isEmpty()) {
            System.out.println("No loan applications found.");
            return;
        }
        applications.forEach(loan -> System.out.printf(
                "ID: %d | %s %s | %s | %s | %s | %s%n", loan.id(), loan.firstName(), loan.lastName(),
                loan.loanType(), loan.loanAmount(), loan.appliedAt(), loan.status()));
    }

    static void printLoanDetail(LoanApplication loan) {
        System.out.println("Application " + loan.id() + " (" + loan.loanType() + ")");
        System.out.println("Applicant: " + loan.firstName() + " " + loan.lastName());
        System.out.println("Amount: " + loan.loanAmount() + " | Tenure: " + loan.durationMonths() + " months");
        System.out.println("Interest rate: " + loan.interestRate() + "%");
        System.out.println("Status: " + loan.status());
        if (loan.emiAmount() != null) {
            System.out.println("Monthly EMI: " + loan.emiAmount());
            System.out.println("Total interest: " + loan.totalInterest());
            System.out.println("Total payable: " + loan.totalPayable());
        } else {
            System.out.println("EMI is available once the application is approved.");
        }
        if (loan.rejectionReason() != null) {
            System.out.println("Rejection reason: " + loan.rejectionReason());
        }
        if (loan.adminNote() != null) {
            System.out.println("Admin note: " + loan.adminNote());
        }
    }

    private long readNumber(String prompt) {
        while (scanner.hasNextLine()) {
            try {
                return Long.parseLong(readLine(prompt));
            } catch (NumberFormatException exception) {
                System.out.println("Enter a whole number.");
            }
        }
        throw new IllegalStateException("Input closed.");
    }

    private BigDecimal readAmount(String prompt) {
        while (scanner.hasNextLine()) {
            String value = readLine(prompt);
            try {
                BigDecimal amount = new BigDecimal(value);
                if (amount.signum() > 0) {
                    return amount;
                }
                System.out.println("Enter an amount greater than zero.");
            } catch (NumberFormatException exception) {
                System.out.println("Enter a valid number.");
            }
        }
        throw new IllegalStateException("Input closed.");
    }

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "";
    }

    private char[] readPassword(String prompt) {
        System.out.print(prompt);
        return (scanner.hasNextLine() ? scanner.nextLine() : "").toCharArray();
    }
}