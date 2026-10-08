package com.loanmanagement.service;

import com.loanmanagement.dao.LoanRepository;
import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.LoanApplication;
import com.loanmanagement.model.LoanStatus;
import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;
import com.loanmanagement.notification.EmailDeliveryException;
import com.loanmanagement.notification.EmailSender;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public final class LoanService {
    private final LoanRepository loans;
    private final InterestRatePolicy interestRates;
    private final EmiCalculator emiCalculator;
    private final EmailSender emailSender;

    public LoanService(
            LoanRepository loans,
            InterestRatePolicy interestRates,
            EmiCalculator emiCalculator,
            EmailSender emailSender) {
        this.loans = loans;
        this.interestRates = interestRates;
        this.emiCalculator = emiCalculator;
        this.emailSender = emailSender;
    }

    public LoanSubmissionResult apply(User user, LoanApplicationCommand command) {
        requireActiveUser(user);
        if (command == null || command.amount() == null || command.amount().signum() <= 0
                || command.annualIncome() == null || command.annualIncome().signum() <= 0
                || command.durationMonths() < 1 || command.durationMonths() > 1_200
                || command.loanType() == null || command.purpose() == null || command.purpose().isBlank()) {
            throw new IllegalArgumentException("Loan details are invalid");
        }
        if (user.cibilScore() == null) {
            throw new IllegalStateException("Complete PAN and CIBIL assessment before applying for a loan");
        }

        BigDecimal rate = interestRates.rateFor(command.loanType(), user.cibilScore());
        LoanApplication application = loans.create(user.id(), command, rate);
        try {
            emailSender.send(user.email(), "Loan Application Submitted",
                    "Dear " + user.firstName() + ", your loan application #" + application.id()
                            + " has been submitted with status Pending.");
        } catch (EmailDeliveryException exception) {
            return new LoanSubmissionResult(application, false);
        }
        return new LoanSubmissionResult(application, true);
    }

    public List<LoanApplication> findMyLoans(User user) {
        requireActiveUser(user);
        return loans.findByUser(user.id());
    }

    public List<LoanApplication> findAllForAdmin(User admin) {
        requireAdmin(admin);
        return loans.findAll();
    }

    public List<LoanApplication> findForUserAsAdmin(User admin, long userId) {
        requireAdmin(admin);
        return loans.findByUser(userId);
    }

    public AdminActionResult review(
            User admin,
            long loanId,
            LoanStatus decision,
            String note) {
        requireAdmin(admin);
        if (decision != LoanStatus.APPROVED && decision != LoanStatus.REJECTED
                && decision != LoanStatus.NEEDS_MORE_INFO) {
            throw new IllegalArgumentException("Unsupported loan decision");
        }
        LoanApplication loan = loans.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Loan application was not found"));
        if (loan.status() != LoanStatus.PENDING && loan.status() != LoanStatus.NEEDS_MORE_INFO) {
            throw new IllegalStateException("Loan application is already in a final state");
        }
        String decisionNote = note == null ? "" : note.trim();
        if ((decision == LoanStatus.REJECTED || decision == LoanStatus.NEEDS_MORE_INFO)
                && decisionNote.isEmpty()) {
            throw new IllegalArgumentException("Provide a reason or requested information");
        }

        LoanCalculation calculation = decision == LoanStatus.APPROVED
                ? emiCalculator.calculate(loan.loanAmount(), loan.interestRate(), loan.durationMonths())
                : null;
        loans.review(admin.id(), loanId, decision, decisionNote, calculation);
        return sendDecisionEmail(loan, decision, decisionNote, calculation);
    }

    private AdminActionResult sendDecisionEmail(
            LoanApplication loan,
            LoanStatus decision,
            String note,
            LoanCalculation calculation) {
        String subject;
        String body;
        if (decision == LoanStatus.APPROVED) {
            subject = "Your Loan Application has been Approved!";
            body = String.format(Locale.ROOT,
                    "Dear %s, your loan application has been approved.%n"
                            + "Sanctioned amount: %s%nLoan type: %s%nTenure: %d months%n"
                            + "Interest rate: %s%%%nMonthly EMI: %s%nTotal interest: %s%nTotal payable: %s",
                    loan.firstName(), loan.loanAmount(), loan.loanType(), loan.durationMonths(),
                    loan.interestRate(), calculation.emi(), calculation.totalInterest(), calculation.totalPayable());
        } else if (decision == LoanStatus.REJECTED) {
            subject = "Your Loan Application has been Rejected";
            body = "Dear " + loan.firstName() + ", we regret to inform you that your loan application has been rejected. "
                    + "Reason: " + note + ". Please contact support for more details.";
        } else {
            subject = "More Information Needed for Your Loan Application";
            body = "Dear " + loan.firstName() + ", please provide the following information: " + note;
        }

        try {
            emailSender.send(loan.email(), subject, body);
            return new AdminActionResult("Loan decision saved and email sent.", true);
        } catch (EmailDeliveryException exception) {
            return new AdminActionResult("Loan decision saved, but email delivery failed.", false);
        }
    }

    private void requireActiveUser(User user) {
        if (user == null || user.status() != AccountStatus.ACTIVE || user.role() != UserRole.ROLE_USER) {
            throw new SecurityException("Active user account required");
        }
    }

    private void requireAdmin(User user) {
        if (user == null || user.status() != AccountStatus.ACTIVE || user.role() != UserRole.ROLE_ADMIN) {
            throw new SecurityException("Admin access required");
        }
    }
}