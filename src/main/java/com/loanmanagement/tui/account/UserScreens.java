package com.loanmanagement.tui.account;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.loanmanagement.model.LoanApplication;
import com.loanmanagement.model.LoanType;
import com.loanmanagement.model.User;
import com.loanmanagement.service.CibilAssessmentResult;
import com.loanmanagement.service.LoanApplicationCommand;
import com.loanmanagement.service.LoanSubmissionResult;
import com.loanmanagement.tui.component.ChoiceField;
import com.loanmanagement.tui.component.Form;
import com.loanmanagement.tui.component.TextField;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.screen.FormScreen;
import com.loanmanagement.tui.screen.HomeScreen;
import com.loanmanagement.tui.screen.MessageScreen;
import com.loanmanagement.tui.screen.Navigation;
import com.loanmanagement.tui.screen.Screen;

/** Regular-user menu: loans, apply, PAN and CIBIL, and profile. Follows the console's user menu. */
public final class UserScreens {
    private static final int TEXT = 100;

    private UserScreens() {
    }

    public static Screen home(AppContext ctx, Session session) {
        User user = session.user();
        return new HomeScreen("Welcome, " + user.firstName() + " " + user.lastName(),
                List.of("Run CIBIL assessment first to unlock applications.",
                        "Press Enter on a loan in My loans for its details."),
                List.of(
                        new HomeScreen.Entry("Apply for loan", () -> new Navigation.Push(apply(ctx, session))),
                        new HomeScreen.Entry("My loans", () -> new Navigation.Push(loans(ctx, session))),
                        new HomeScreen.Entry("PAN and CIBIL", () -> new Navigation.Push(cibil(ctx, session))),
                        new HomeScreen.Entry("My profile", () -> new Navigation.Push(profile(session))),
                        new HomeScreen.Entry("Logout", Navigation.Pop::new)),
                session::statusLine);
    }

    static Screen loans(AppContext ctx, Session session) {
        try {
            List<LoanApplication> loans = ctx.loans().findMyLoans(session.user());
            session.setLoanCount(loans.size());
            return new LoansScreen(loans);
        } catch (RuntimeException exception) {
            return new MessageScreen("My loans", List.of("Could not load your loans. Try again later."),
                    Tone.DANGER, Navigation.Pop::new);
        }
    }

    static Screen apply(AppContext ctx, Session session) {
        TextField amount = new TextField("Loan amount", false, 15);
        ChoiceField<LoanType> type = new ChoiceField<>("Loan type",
                List.of(LoanType.values()), UserScreens::loanTypeLabel);
        TextField months = new TextField("Tenure in months", false, 4);
        TextField income = new TextField("Annual income", false, 15);
        TextField purpose = new TextField("Purpose", false, TEXT);
        Form form = new Form(List.of(amount, type, months, income, purpose));
        return new FormScreen("Apply for loan", "Your application starts as Pending.", form, f -> {
            BigDecimal loanAmount;
            int tenure;
            BigDecimal annualIncome;
            try {
                loanAmount = positiveAmount(amount.value());
                tenure = positiveWhole(months.value());
                annualIncome = positiveAmount(income.value());
            } catch (IllegalArgumentException exception) {
                f.setFormError(exception.getMessage());
                return Navigation.STAY;
            }
            if (purpose.value().isBlank()) {
                f.setFormError("Purpose is required.");
                return Navigation.STAY;
            }
            try {
                LoanSubmissionResult result = ctx.loans().apply(session.user(), new LoanApplicationCommand(
                        loanAmount, type.value(), tenure, annualIncome, purpose.value()));
                session.setLoanCount(session.loanCount() + 1);
                List<String> lines = new ArrayList<>(List.of(
                        "Application " + result.application().id() + " submitted as Pending."));
                if (!result.notificationSent()) {
                    lines.add("Application saved, but confirmation email was not sent.");
                }
                return new Navigation.Replace(new MessageScreen("Apply for loan", lines, Tone.SUCCESS,
                        Navigation.Pop::new));
            } catch (IllegalArgumentException exception) {
                f.setFormError("Enter valid loan details. " + exception.getMessage());
                return Navigation.STAY;
            } catch (IllegalStateException exception) {
                f.setFormError(exception.getMessage());
                return Navigation.STAY;
            }
        });
    }

    static Screen cibil(AppContext ctx, Session session) {
        TextField pan = new TextField("PAN", false, 10);
        ChoiceField<LoanType> type = new ChoiceField<>("Loan type",
                List.of(LoanType.values()), UserScreens::loanTypeLabel);
        Form form = new Form(List.of(pan, type));
        return new FormScreen("PAN and CIBIL", "Checks your PAN format and generates your CIBIL score.", form, f -> {
            try {
                CibilAssessmentResult result = ctx.cibil().assess(session.user(), pan.value(), type.value());
                session.update(result.user());
                return new Navigation.Replace(new MessageScreen("PAN and CIBIL", List.of(
                        "CIBIL score: " + result.user().cibilScore(),
                        "Interest rate: " + result.interestRate() + "%"), Tone.SUCCESS, Navigation.Pop::new));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                f.setFormError(exception.getMessage());
                return Navigation.STAY;
            }
        });
    }

    static Screen profile(Session session) {
        User user = session.user();
        return new MessageScreen("My profile", List.of(
                "Username: " + user.username(),
                "Name: " + user.firstName() + " " + user.lastName(),
                "Email: " + user.email(),
                "Mobile: " + user.mobile(),
                "Location: " + (user.location() == null ? "Not provided" : user.location()),
                "PAN: " + (user.pan() == null ? "Not assessed" : user.pan()),
                "CIBIL score: " + (user.cibilScore() == null ? "Not assessed" : user.cibilScore()),
                "Status: " + user.status()), Tone.PLAIN, Navigation.Pop::new);
    }

    /** Parses a positive amount. Messages match the console's wording. */
    static BigDecimal positiveAmount(String raw) {
        BigDecimal value;
        try {
            value = new BigDecimal(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid number.");
        }
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Enter an amount greater than zero.");
        }
        return value;
    }

    static int positiveWhole(String raw) {
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole number.");
        }
        if (value < 1) {
            throw new IllegalArgumentException("Enter a whole number of months, at least 1.");
        }
        return value;
    }

    public static String loanTypeLabel(LoanType type) {
        String name = type.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " loan";
    }
}
