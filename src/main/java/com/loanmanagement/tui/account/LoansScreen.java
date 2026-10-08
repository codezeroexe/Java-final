package com.loanmanagement.tui.account;

import java.util.List;
import java.util.OptionalInt;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.loanmanagement.model.LoanApplication;
import com.loanmanagement.model.LoanStatus;
import com.loanmanagement.tui.component.StatusBar;
import com.loanmanagement.tui.component.Table;
import com.loanmanagement.tui.component.Table.Cell;
import com.loanmanagement.tui.render.TerminalRenderer;
import com.loanmanagement.tui.render.TerminalRenderer.Tone;
import com.loanmanagement.tui.screen.Area;
import com.loanmanagement.tui.screen.MessageScreen;
import com.loanmanagement.tui.screen.Navigation;
import com.loanmanagement.tui.screen.Screen;

/** Applications as a table. Enter opens one application's details. Admins see the applicant column too. */
public final class LoansScreen implements Screen {
    private static final String DOT = "● ";

    private final String title;
    private final List<LoanApplication> loans;
    private final Table table;

    public LoansScreen(List<LoanApplication> loans) {
        this("My loans", loans, false);
    }

    public LoansScreen(String title, List<LoanApplication> loans, boolean showApplicant) {
        this.title = title;
        this.loans = List.copyOf(loans);
        this.table = showApplicant
                ? new Table(List.of("#", "Applicant", "Type", "Amount", "Status", "EMI"),
                        new int[] {4, 16, 10, 14, 14, 12},
                        loans.stream().map(loan -> rowWithApplicant(loan)).toList())
                : new Table(List.of("#", "Type", "Amount", "Status", "EMI"),
                        new int[] {4, 10, 14, 14, 12},
                        loans.stream().map(loan -> row(loan)).toList());
    }

    private static List<Cell> row(LoanApplication loan) {
        return List.of(
                Cell.plain(String.valueOf(loan.id())),
                Cell.plain(UserScreens.loanTypeLabel(loan.loanType())),
                Cell.plain("₹" + loan.loanAmount().toPlainString()),
                statusCell(loan.status()),
                emiCell(loan));
    }

    private static List<Cell> rowWithApplicant(LoanApplication loan) {
        return List.of(
                Cell.plain(String.valueOf(loan.id())),
                Cell.plain(loan.firstName() + " " + loan.lastName()),
                Cell.plain(UserScreens.loanTypeLabel(loan.loanType())),
                Cell.plain("₹" + loan.loanAmount().toPlainString()),
                statusCell(loan.status()),
                emiCell(loan));
    }

    private static Cell emiCell(LoanApplication loan) {
        return Cell.plain(loan.emiAmount() == null ? "—" : "₹" + loan.emiAmount().toPlainString());
    }

    private static Cell statusCell(LoanStatus status) {
        return switch (status) {
            case APPROVED -> new Cell(DOT + "Approved", Tone.SUCCESS);
            case PENDING -> new Cell(DOT + "Pending", Tone.WARNING);
            case REJECTED -> new Cell(DOT + "Rejected", Tone.DANGER);
            case NEEDS_MORE_INFO -> new Cell(DOT + "More info", Tone.WARNING);
        };
    }

    @Override
    public void render(TerminalRenderer renderer, Area area) {
        renderer.text(area.column(), area.row(), title, Tone.WARNING);
        int hintRow = area.row() + area.height() - 1;
        if (loans.isEmpty()) {
            renderer.text(area.column(), area.row() + 2, "No loan applications yet.", Tone.MUTED);
        } else {
            table.render(renderer, new Area(area.column(), area.row() + 2, area.width(),
                    Math.max(0, hintRow - area.row() - 3)));
        }
        StatusBar.hints(renderer, new Area(area.column(), hintRow, area.width(), 1), List.of(
                new StatusBar.Hint("↑↓", "move"),
                new StatusBar.Hint("Enter", "details"),
                new StatusBar.Hint("Esc", "back")));
    }

    @Override
    public Navigation onKey(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) {
            return new Navigation.Pop();
        }
        OptionalInt chosen = table.handle(key);
        if (chosen.isPresent()) {
            LoanApplication loan = loans.get(chosen.getAsInt());
            return new Navigation.Push(new MessageScreen(
                    "Application " + loan.id() + " (" + UserScreens.loanTypeLabel(loan.loanType()) + ")",
                    detailLines(loan), Tone.PLAIN, Navigation.Pop::new));
        }
        return Navigation.STAY;
    }

    /** Detail lines mirror the console's loan detail view. */
    public static List<String> detailLines(LoanApplication loan) {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>(List.of(
                "Applicant: " + loan.firstName() + " " + loan.lastName(),
                "Amount: " + loan.loanAmount() + "  ·  Tenure: " + loan.durationMonths() + " months",
                "Interest rate: " + loan.interestRate() + "%",
                "Status: " + loan.status()));
        if (loan.emiAmount() != null) {
            lines.add("Monthly EMI: " + loan.emiAmount());
            lines.add("Total interest: " + loan.totalInterest());
            lines.add("Total payable: " + loan.totalPayable());
        } else {
            lines.add("EMI is available once the application is approved.");
        }
        if (loan.rejectionReason() != null) {
            lines.add("Rejection reason: " + loan.rejectionReason());
        }
        if (loan.adminNote() != null) {
            lines.add("Admin note: " + loan.adminNote());
        }
        return lines;
    }
}
