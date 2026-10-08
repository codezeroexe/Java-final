package com.loanmanagement.tui.account;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import com.loanmanagement.model.LoanType;
import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;
import com.loanmanagement.service.AuthenticationResult;
import com.loanmanagement.service.AuthenticationStatus;
import com.loanmanagement.service.CibilOnboardingCommand;
import com.loanmanagement.service.CibilOnboardingResult;
import com.loanmanagement.service.RegisterCommand;
import com.loanmanagement.tui.component.ChoiceField;
import com.loanmanagement.tui.component.Field;
import com.loanmanagement.tui.admin.AdminScreens;
import com.loanmanagement.tui.component.Form;
import com.loanmanagement.tui.component.TextField;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.screen.FormScreen;
import com.loanmanagement.tui.screen.HomeScreen;
import com.loanmanagement.tui.screen.MessageScreen;
import com.loanmanagement.tui.screen.Navigation;
import com.loanmanagement.tui.screen.Screen;

/**
 * Root menu and account flows: register, login, CIBIL onboarding, forgot password, and the
 * temporary-password rotation. Messages and branching follow the console flows.
 */
public final class AccountScreens {
    private static final int TEXT = 100;
    private static final int SECRET = 128;
    private static final String RESET_NOTICE =
            "If an active account matches, a reset token was sent to its registered email.";

    private AccountScreens() {
    }

    public static Screen root(AppContext ctx) {
        return new HomeScreen("Loan Management System",
                List.of("Apply for a loan from the user menu after login.",
                        "Run CIBIL assessment first to unlock applications."),
                List.of(
                        new HomeScreen.Entry("Register", () -> new Navigation.Push(register(ctx))),
                        new HomeScreen.Entry("Login", () -> new Navigation.Push(login(ctx))),
                        new HomeScreen.Entry("CIBIL onboarding", () -> new Navigation.Push(onboarding(ctx))),
                        new HomeScreen.Entry("Forgot password", () -> new Navigation.Push(forgotPassword(ctx))),
                        new HomeScreen.Entry("Exit", Navigation.Quit::new)));
    }

    static Screen register(AppContext ctx) {
        TextField first = new TextField("First name", false, TEXT);
        TextField last = new TextField("Last name", false, TEXT);
        TextField mobile = new TextField("Mobile (10 digits)", false, 10);
        TextField email = new TextField("Email", false, TEXT);
        TextField location = new TextField("Location (optional)", false, TEXT);
        TextField username = new TextField("Username", false, TEXT);
        TextField password = new TextField("Password", true, SECRET);
        Form form = new Form(List.of(first, last, mobile, email, location, username, password));
        return new FormScreen("Register", null, form, f -> {
            char[] chars = password.value().toCharArray();
            try {
                User user = ctx.authentication().register(new RegisterCommand(first.value(), last.value(),
                        mobile.value(), email.value(), location.value(), username.value(), chars));
                return new Navigation.Replace(message("Register",
                        List.of("Registration successful for " + user.username() + "."), Tone.SUCCESS));
            } catch (IllegalArgumentException exception) {
                f.setFormError(exception.getMessage());
            } catch (RuntimeException exception) {
                f.setFormError("Could not register account. Check whether username or email is already in use.");
            } finally {
                Arrays.fill(chars, '\0');
                password.clear();
            }
            return Navigation.STAY;
        });
    }

    static Screen login(AppContext ctx) {
        TextField identifier = new TextField("Email or username", false, TEXT);
        TextField password = new TextField("Password", true, SECRET);
        Form form = new Form(List.of(identifier, password));
        return new FormScreen("Login", null, form, f -> {
            char[] chars = password.value().toCharArray();
            AuthenticationResult result;
            try {
                result = ctx.authentication().authenticate(identifier.value(), chars);
            } catch (RuntimeException exception) {
                f.setFormError("Login service is unavailable. Try again later.");
                return Navigation.STAY;
            } finally {
                Arrays.fill(chars, '\0');
                password.clear();
            }
            return switch (result.status()) {
                case SUCCESS -> new Navigation.Replace(postSignIn(ctx, result.user().orElseThrow()));
                case INVALID_CREDENTIALS -> {
                    f.setFormError(result.attemptsRemaining() > 0
                            ? "Invalid credentials. " + result.attemptsRemaining()
                                    + " attempts remain before account lockout."
                            : "Invalid credentials.");
                    yield Navigation.STAY;
                }
                case ACCOUNT_LOCKED -> {
                    f.setFormError("Account locked. Contact support or an administrator.");
                    yield Navigation.STAY;
                }
                case ACCOUNT_INACTIVE -> {
                    f.setFormError("Account is inactive. Contact an administrator.");
                    yield Navigation.STAY;
                }
            };
        });
    }

    static Screen onboarding(AppContext ctx) {
        TextField first = new TextField("First name", false, TEXT);
        TextField last = new TextField("Last name", false, TEXT);
        TextField mobile = new TextField("Mobile (10 digits)", false, 10);
        TextField email = new TextField("Email", false, TEXT);
        TextField location = new TextField("Location", false, TEXT);
        TextField pan = new TextField("PAN", false, 10);
        ChoiceField<LoanType> loanType = new ChoiceField<>("Loan type",
                List.of(LoanType.values()), UserScreens::loanTypeLabel);
        Form form = new Form(List.<Field>of(first, last, mobile, email, location, pan, loanType));
        return new FormScreen("CIBIL onboarding", "Creates your account and assesses your CIBIL score.", form, f -> {
            try {
                CibilOnboardingResult result = ctx.cibil().onboard(new CibilOnboardingCommand(
                        first.value(), last.value(), mobile.value(), email.value(), location.value(),
                        pan.value(), loanType.value()));
                User user = result.user();
                List<String> lines = new java.util.ArrayList<>(List.of(
                        "CIBIL score: " + user.cibilScore(),
                        "Interest rate: " + result.interestRate() + "%",
                        "Username: " + user.username()));
                if (!result.credentialsEmailSent()) {
                    lines.add("Credentials email failed. Temporary password: " + result.recoveryPassword());
                }
                return new Navigation.Replace(new MessageScreen("CIBIL onboarding", lines, Tone.SUCCESS,
                        () -> new Navigation.Replace(postSignIn(ctx, user))));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                f.setFormError(exception.getMessage());
                return Navigation.STAY;
            }
        });
    }

    static Screen forgotPassword(AppContext ctx) {
        TextField identifier = new TextField("Email or username", false, TEXT);
        Form form = new Form(List.of(identifier));
        return new FormScreen("Forgot password", "Enter your email or username.", form, f -> {
            ctx.passwordResets().requestReset(identifier.value());
            return new Navigation.Replace(resetPassword(ctx));
        });
    }

    static Screen resetPassword(AppContext ctx) {
        TextField token = new TextField("Reset token", false, 64);
        TextField password = new TextField("New password", true, SECRET);
        Form form = new Form(List.of(token, password));
        return new FormScreen("Reset password", RESET_NOTICE, form, f -> {
            if (token.value().isBlank()) {
                f.setFormError("Enter the reset token from your email, or press Esc to cancel.");
                return Navigation.STAY;
            }
            char[] chars = password.value().toCharArray();
            try {
                if (ctx.passwordResets().resetPassword(token.value().trim(), chars)) {
                    return new Navigation.Replace(message("Reset password",
                            List.of("Password reset successful. Log in with your new password."), Tone.SUCCESS));
                }
                f.setFormError("Reset token is invalid, expired, or already used.");
                return Navigation.STAY;
            } catch (IllegalArgumentException exception) {
                f.setFormError(exception.getMessage());
                return Navigation.STAY;
            } finally {
                Arrays.fill(chars, '\0');
                password.clear();
            }
        });
    }

    static Screen changePassword(AppContext ctx, User user) {
        TextField password = new TextField("New password", true, SECRET);
        Form form = new Form(List.of(password));
        return new FormScreen("Change temporary password",
                "Your password is temporary. Set a new one to continue.", form, f -> {
                    char[] chars = password.value().toCharArray();
                    try {
                        ctx.authentication().changePassword(user, chars);
                        return new Navigation.Replace(postSignIn(ctx, user.withMustChangePassword(false)));
                    } catch (IllegalArgumentException | IllegalStateException exception) {
                        f.setFormError(exception.getMessage());
                        return Navigation.STAY;
                    } finally {
                        Arrays.fill(chars, '\0');
                        password.clear();
                    }
                });
    }

    /** Where a successful sign-in lands: password change first when the password is temporary. */
    static Screen postSignIn(AppContext ctx, User user) {
        if (user.mustChangePassword()) {
            return changePassword(ctx, user);
        }
        if (user.role() == UserRole.ROLE_ADMIN) {
            return AdminScreens.home(ctx, new Session(user));
        }
        return UserScreens.home(ctx, new Session(user));
    }

    private static Screen message(String title, List<String> lines, Tone tone) {
        return new MessageScreen(title, lines, tone, Navigation.Pop::new);
    }

}
