package com.loanmanagement.tui;

import java.util.List;

import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.screen.HomeScreen;
import com.loanmanagement.tui.screen.Navigation;
import com.loanmanagement.tui.screen.TuiApplication;

/** Manual preview of the home screen on a real terminal. Replaced by the real launcher in Phase 3. */
public final class TuiPreview {
    private TuiPreview() {
    }

    public static void main(String[] args) throws Exception {
        try (TerminalRenderer renderer = TerminalRenderer.openTerminal()) {
            HomeScreen home = new HomeScreen("Loan Management System",
                    List.of("Apply for a loan from the user menu after login.",
                            "Run CIBIL assessment first to unlock applications."),
                    List.of(new HomeScreen.Entry("Register", () -> Navigation.STAY),
                            new HomeScreen.Entry("Login", () -> Navigation.STAY),
                            new HomeScreen.Entry("CIBIL onboarding", () -> Navigation.STAY),
                            new HomeScreen.Entry("Forgot password", () -> Navigation.STAY),
                            new HomeScreen.Entry("Exit", Navigation.Quit::new)));
            new TuiApplication(renderer).run(home);
        }
    }
}
