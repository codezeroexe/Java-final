package com.loanmanagement.cli;

import java.util.List;
import java.util.Scanner;

import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.LoanApplication;
import com.loanmanagement.model.LoanStatus;
import com.loanmanagement.model.User;
import com.loanmanagement.service.AdminActionResult;
import com.loanmanagement.service.AdminUserService;
import com.loanmanagement.service.LoanService;
import com.loanmanagement.service.UserProfileUpdate;
import com.loanmanagement.service.UserSearchField;

public final class AdminConsoleMenu {
    private final Scanner scanner;
    private final AdminUserService adminUsers;
    private final LoanService loans;

    public AdminConsoleMenu(Scanner scanner, AdminUserService adminUsers, LoanService loans) {
        this.scanner = scanner;
        this.adminUsers = adminUsers;
        this.loans = loans;
    }

    public void run(User admin) {
        while (scanner.hasNextLine()) {
            System.out.println("\nAdmin Dashboard");
            System.out.println("1. Search users");
            System.out.println("2. View user details");
            System.out.println("3. Edit user information");
            System.out.println("4. Activate user");
            System.out.println("5. Deactivate user");
            System.out.println("6. Delete user");
            System.out.println("7. View all loans");
            System.out.println("8. View loan details");
            System.out.println("9. Review loan");
            System.out.println("10. Logout");
            String choice = readLine("Select option: ");
            try {
                switch (choice) {
                    case "1" -> searchUsers(admin);
                    case "2" -> viewUser(admin);
                    case "3" -> editUser(admin);
                    case "4" -> changeStatus(admin, AccountStatus.ACTIVE);
                    case "5" -> changeStatus(admin, AccountStatus.INACTIVE);
                    case "6" -> deleteUser(admin);
                    case "7" -> ConsoleApplication.printLoans(loans.findAllForAdmin(admin));
                    case "8" -> viewLoanDetails(loans.findAllForAdmin(admin));
                    case "9" -> reviewLoan(admin);
                    case "10" -> { return; }
                    default -> System.out.println("Invalid option.");
                }
            } catch (IllegalArgumentException | SecurityException exception) {
                System.out.println(exception.getMessage());
            }
        }
    }

    private void searchUsers(User admin) {
        UserSearchField field = readSearchField();
        List<User> matches = adminUsers.searchUsers(admin, field, readLine("Search value: "));
        if (matches.isEmpty()) {
            System.out.println("No users found.");
        }
        matches.forEach(this::printUser);
    }

    private UserSearchField readSearchField() {
        UserSearchField[] fields = UserSearchField.values();
        while (scanner.hasNextLine()) {
            for (int index = 0; index < fields.length; index++) {
                System.out.println((index + 1) + ". " + fields[index].name());
            }
            long selected = readNumber("Search field: ");
            if (selected >= 1 && selected <= fields.length) {
                return fields[(int) selected - 1];
            }
            System.out.println("Invalid search field.");
        }
        throw new IllegalStateException("Input closed.");
    }

    private void viewUser(User admin) {
        User user = adminUsers.viewUser(admin, readNumber("User ID: "))
                .orElseThrow(() -> new IllegalArgumentException("User was not found"));
        printUser(user);
        System.out.println("Mobile: " + user.mobile());
        System.out.println("Location: " + (user.location() == null ? "Not provided" : user.location()));
        ConsoleApplication.printLoans(loans.findForUserAsAdmin(admin, user.id()));
    }

    private void editUser(User admin) {
        long userId = readNumber("User ID: ");
        UserProfileUpdate update = new UserProfileUpdate(
                readLine("First name: "), readLine("Last name: "), readLine("Mobile: "),
                readLine("Email: "), readLine("Location: "));
        printAction(adminUsers.updateUser(admin, userId, update));
    }

    private void changeStatus(User admin, AccountStatus status) {
        long userId = readNumber("User ID: ");
        printAction(adminUsers.setUserStatus(admin, userId, status));
    }

    private void deleteUser(User admin) {
        long userId = readNumber("User ID: ");
        boolean confirmed = "Y".equalsIgnoreCase(readLine("Anonymize and delete this account? (Y/N): "));
        printAction(adminUsers.deleteUser(admin, userId, confirmed));
    }

    private void viewLoanDetails(List<LoanApplication> applications) {
        if (applications.isEmpty()) {
            System.out.println("No loan applications found.");
            return;
        }
        ConsoleApplication.printLoans(applications);
        long loanId = readNumber("Loan ID to inspect: ");
        applications.stream()
                .filter(loan -> loan.id() == loanId)
                .findFirst()
                .ifPresentOrElse(ConsoleApplication::printLoanDetail,
                        () -> System.out.println("No loan application with ID " + loanId + "."));
    }

    private void reviewLoan(User admin) {
        long loanId = readNumber("Loan ID: ");
        LoanStatus status = readDecision();
        String note = status == LoanStatus.APPROVED ? "" : readLine("Reason/details: ");
        if (status != LoanStatus.APPROVED && note.isBlank()) {
            System.out.println("A reason or requested information is required.");
            return;
        }
        printAction(loans.review(admin, loanId, status, note));
    }

    private LoanStatus readDecision() {
        while (scanner.hasNextLine()) {
            System.out.println("1. Approve");
            System.out.println("2. Reject");
            System.out.println("3. Needs more information");
            LoanStatus status = switch (readLine("Decision: ")) {
                case "1" -> LoanStatus.APPROVED;
                case "2" -> LoanStatus.REJECTED;
                case "3" -> LoanStatus.NEEDS_MORE_INFO;
                default -> null;
            };
            if (status != null) {
                return status;
            }
            System.out.println("Invalid loan decision.");
        }
        throw new IllegalStateException("Input closed.");
    }

    private void printAction(AdminActionResult result) {
        System.out.println(result.message());
    }

    private void printUser(User user) {
        System.out.printf("ID: %d | %s %s | %s | %s%n", user.id(), user.firstName(), user.lastName(),
                user.email(), user.status());
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

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "";
    }
}