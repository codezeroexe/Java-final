package com.loanmanagement.tui.admin;

import java.util.ArrayList;
import java.util.List;

import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.LoanApplication;
import com.loanmanagement.model.LoanStatus;
import com.loanmanagement.model.LoanType;
import com.loanmanagement.model.User;
import com.loanmanagement.service.AdminActionResult;
import com.loanmanagement.service.EmiCalculator;
import com.loanmanagement.service.LoanCalculation;
import com.loanmanagement.service.UserProfileUpdate;
import com.loanmanagement.service.UserSearchField;
import com.loanmanagement.tui.account.AppContext;
import com.loanmanagement.tui.account.LoansScreen;
import com.loanmanagement.tui.account.Session;
import com.loanmanagement.tui.component.ChoiceField;
import com.loanmanagement.tui.component.Dialog;
import com.loanmanagement.tui.component.Field;
import com.loanmanagement.tui.component.Form;
import com.loanmanagement.tui.component.TextField;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.screen.DialogScreen;
import com.loanmanagement.tui.screen.FormScreen;
import com.loanmanagement.tui.screen.HomeScreen;
import com.loanmanagement.tui.screen.MessageScreen;
import com.loanmanagement.tui.screen.Navigation;
import com.loanmanagement.tui.screen.Screen;
import com.loanmanagement.tui.account.UserScreens;

/**
 * Admin dashboard. Each action calls the same admin service method as the console and reports the same
 * result text. Delete needs the explicit 'd' key. Approval previews the EMI before it is confirmed.
 */
public final class AdminScreens {
    private static final int TEXT = 100;
    private static final char DELETE_KEY = 'd';
    private static final int DETAIL_LOAN_LIMIT = 5;

    private AdminScreens() {
    }

    public static Screen home(AppContext ctx, Session session) {
        User admin = session.user();
        return new HomeScreen("Admin dashboard",
                List.of("Actions run as your admin account.",
                        "Deleting needs an explicit 'd' key press. Enter cancels."),
                List.of(
                        new HomeScreen.Entry("Search users", () -> push(searchForm(ctx, admin))),
                        new HomeScreen.Entry("View user details", () -> push(userIdForm("View user details",
                                id -> userDetail(ctx, admin, id)))),
                        new HomeScreen.Entry("Edit user information", () -> push(editForm(ctx, admin))),
                        new HomeScreen.Entry("Activate user", () -> push(statusForm(ctx, admin,
                                "Activate user", AccountStatus.ACTIVE))),
                        new HomeScreen.Entry("Deactivate user", () -> push(statusForm(ctx, admin,
                                "Deactivate user", AccountStatus.INACTIVE))),
                        new HomeScreen.Entry("Delete user", () -> push(deleteForm(ctx, admin))),
                        new HomeScreen.Entry("View all loans", () -> push(allLoans(ctx, admin))),
                        new HomeScreen.Entry("View loan details", () -> push(loanIdForm(ctx, admin))),
                        new HomeScreen.Entry("Review loan", () -> push(reviewForm(ctx, admin))),
                        new HomeScreen.Entry("Logout", Navigation.Pop::new)),
                session::statusLine);
    }

    private static Navigation push(Screen screen) {
        return new Navigation.Push(screen);
    }

    static Screen searchForm(AppContext ctx, User admin) {
        ChoiceField<UserSearchField> field = new ChoiceField<>("Search by",
                List.of(UserSearchField.values()), AdminScreens::fieldLabel);
        TextField query = new TextField("Search value", false, TEXT);
        return new FormScreen("Search users", null, new Form(List.of(field, query)), f -> {
            try {
                List<User> matches = ctx.adminUsers().searchUsers(admin, field.value(), query.value());
                if (matches.isEmpty()) {
                    return new Navigation.Replace(message("Search users", List.of("No users found."), Tone.PLAIN));
                }
                return new Navigation.Replace(new UserTableScreen("Search results", matches,
                        user -> push(userDetail(ctx, admin, user.id()))));
            } catch (IllegalArgumentException | SecurityException exception) {
                f.setFormError(exception.getMessage());
                return Navigation.STAY;
            }
        });
    }

    static Screen userDetail(AppContext ctx, User admin, long userId) {
        try {
            User user = ctx.adminUsers().viewUser(admin, userId)
                    .orElseThrow(() -> new IllegalArgumentException("User was not found"));
            List<String> lines = new ArrayList<>(List.of(
                    "ID: " + user.id(),
                    "Name: " + user.firstName() + " " + user.lastName(),
                    "Username: " + user.username(),
                    "Email: " + user.email(),
                    "Mobile: " + user.mobile(),
                    "Location: " + (user.location() == null ? "Not provided" : user.location()),
                    "Status: " + user.status()));
            List<LoanApplication> loans = ctx.loans().findForUserAsAdmin(admin, user.id());
            lines.add(loans.isEmpty() ? "Loans: none" : "Loans: " + loans.size());
            loans.stream().limit(DETAIL_LOAN_LIMIT).forEach(loan -> lines.add(
                    "  #" + loan.id() + "  " + UserScreens.loanTypeLabel(loan.loanType())
                            + "  ₹" + loan.loanAmount().toPlainString() + "  " + loan.status()));
            return new MessageScreen("User " + user.id(), lines, Tone.PLAIN, Navigation.Pop::new);
        } catch (IllegalArgumentException | SecurityException exception) {
            return message("User details", List.of(exception.getMessage()), Tone.DANGER);
        }
    }

    /** Asks for a user ID, then hands it to {@code onId}. Used by view, activate, and deactivate. */
    static Screen userIdForm(String title, java.util.function.LongFunction<Screen> onId) {
        TextField id = new TextField("User ID", false, 12);
        return new FormScreen(title, null, new Form(List.of(id)), f -> {
            try {
                return new Navigation.Replace(onId.apply(parseId(id.value())));
            } catch (IllegalArgumentException | SecurityException exception) {
                f.setFormError(exception.getMessage());
                return Navigation.STAY;
            }
        });
    }

    static Screen statusForm(AppContext ctx, User admin, String title, AccountStatus status) {
        return userIdForm(title, id -> actionMessage(title, ctx.adminUsers().setUserStatus(admin, id, status)));
    }

    static Screen editForm(AppContext ctx, User admin) {
        TextField id = new TextField("User ID", false, 12);
        TextField first = new TextField("First name", false, TEXT);
        TextField last = new TextField("Last name", false, TEXT);
        TextField mobile = new TextField("Mobile", false, 10);
        TextField email = new TextField("Email", false, TEXT);
        TextField location = new TextField("Location", false, TEXT);
        Form form = new Form(List.<Field>of(id, first, last, mobile, email, location));
        return new FormScreen("Edit user information", "Enter all profile fields. They replace the current values.",
                form, f -> {
                    try {
                        AdminActionResult result = ctx.adminUsers().updateUser(admin, parseId(id.value()),
                                new UserProfileUpdate(first.value(), last.value(), mobile.value(),
                                        email.value(), location.value()));
                        return new Navigation.Replace(actionMessage("Edit user information", result));
                    } catch (IllegalArgumentException | SecurityException | IllegalStateException exception) {
                        f.setFormError(exception.getMessage());
                        return Navigation.STAY;
                    }
                });
    }

    static Screen deleteForm(AppContext ctx, User admin) {
        TextField id = new TextField("User ID", false, 12);
        return new FormScreen("Delete user", "Anonymizes personal data. Loan and audit history is kept.",
                new Form(List.of(id)), f -> {
                    long userId;
                    try {
                        userId = parseId(id.value());
                    } catch (IllegalArgumentException exception) {
                        f.setFormError(exception.getMessage());
                        return Navigation.STAY;
                    }
                    Dialog dialog = Dialog.destructive("Delete user",
                            "Anonymize and delete user " + userId + "?", DELETE_KEY);
                    return new Navigation.Replace(new DialogScreen(dialog, confirmed -> {
                        if (!confirmed) {
                            return new Navigation.Pop();
                        }
                        try {
                            return new Navigation.Replace(actionMessage("Delete user",
                                    ctx.adminUsers().deleteUser(admin, userId, true)));
                        } catch (IllegalArgumentException | SecurityException exception) {
                            return new Navigation.Replace(message("Delete user", List.of(exception.getMessage()), Tone.DANGER));
                        }
                    }));
                });
    }

    static Screen allLoans(AppContext ctx, User admin) {
        try {
            List<LoanApplication> loans = ctx.loans().findAllForAdmin(admin);
            return new LoansScreen("All loans", loans, true);
        } catch (RuntimeException exception) {
            return message("All loans", List.of("Could not load loans. Try again later."), Tone.DANGER);
        }
    }

    static Screen loanIdForm(AppContext ctx, User admin) {
        TextField id = new TextField("Loan ID", false, 12);
        return new FormScreen("Loan details", null, new Form(List.of(id)), f -> {
            try {
                long loanId = parseId(id.value());
                LoanApplication loan = ctx.loans().findAllForAdmin(admin).stream()
                        .filter(candidate -> candidate.id() == loanId)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No loan application with ID " + loanId + "."));
                return new Navigation.Replace(new MessageScreen("Application " + loan.id(),
                        LoansScreen.detailLines(loan), Tone.PLAIN, Navigation.Pop::new));
            } catch (IllegalArgumentException | SecurityException exception) {
                f.setFormError(exception.getMessage());
                return Navigation.STAY;
            }
        });
    }

    static Screen reviewForm(AppContext ctx, User admin) {
        TextField id = new TextField("Loan ID", false, 12);
        ChoiceField<LoanStatus> decision = new ChoiceField<>("Decision",
                List.of(LoanStatus.APPROVED, LoanStatus.REJECTED, LoanStatus.NEEDS_MORE_INFO),
                AdminScreens::decisionLabel);
        TextField note = new TextField("Reason or details (required unless approving)", false, 500);
        Form form = new Form(List.<Field>of(id, decision, note));
        return new FormScreen("Review loan", null, form, f -> {
            try {
                long loanId = parseId(id.value());
                LoanStatus status = decision.value();
                String text = note.value().trim();
                if (status != LoanStatus.APPROVED && text.isEmpty()) {
                    f.setFormError("A reason or requested information is required.");
                    return Navigation.STAY;
                }
                if (status == LoanStatus.APPROVED) {
                    LoanApplication loan = ctx.loans().findAllForAdmin(admin).stream()
                            .filter(candidate -> candidate.id() == loanId)
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Loan application was not found"));
                    LoanCalculation calc = new EmiCalculator().calculate(loan.loanAmount(),
                            loan.interestRate(), loan.durationMonths());
                    String summary = "EMI ₹" + calc.emi() + " a month  ·  total interest ₹"
                            + calc.totalInterest() + "  ·  total payable ₹" + calc.totalPayable();
                    Dialog dialog = Dialog.confirm("Approve loan " + loanId, summary);
                    return new Navigation.Replace(new DialogScreen(dialog, confirmed -> {
                        if (!confirmed) {
                            return new Navigation.Pop();
                        }
                        return reviewMessage(ctx, admin, loanId, status, text);
                    }));
                }
                return reviewMessage(ctx, admin, loanId, status, text);
            } catch (IllegalArgumentException | IllegalStateException | SecurityException exception) {
                f.setFormError(exception.getMessage());
                return Navigation.STAY;
            }
        });
    }

    private static Navigation reviewMessage(AppContext ctx, User admin, long loanId, LoanStatus status,
            String note) {
        try {
            AdminActionResult result = ctx.loans().review(admin, loanId, status, note);
            return new Navigation.Replace(actionMessage("Review loan", result));
        } catch (IllegalArgumentException | IllegalStateException | SecurityException exception) {
            return new Navigation.Replace(message("Review loan", List.of(exception.getMessage()), Tone.DANGER));
        }
    }

    private static Screen actionMessage(String title, AdminActionResult result) {
        return new MessageScreen(title, List.of(result.message()),
                result.notificationSent() ? Tone.SUCCESS : Tone.WARNING, Navigation.Pop::new);
    }

    private static Screen message(String title, List<String> lines, Tone tone) {
        return new MessageScreen(title, lines, tone, Navigation.Pop::new);
    }

    static long parseId(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole number.");
        }
    }

    private static String fieldLabel(UserSearchField field) {
        return switch (field) {
            case FIRST_NAME -> "First name";
            case LAST_NAME -> "Last name";
            case EMAIL -> "Email";
            case MOBILE -> "Mobile";
            case USERNAME -> "Username";
            case STATUS -> "Status";
        };
    }

    private static String decisionLabel(LoanStatus status) {
        return switch (status) {
            case APPROVED -> "Approve";
            case REJECTED -> "Reject";
            case NEEDS_MORE_INFO -> "Needs more information";
            case PENDING -> "Pending";
        };
    }
}
