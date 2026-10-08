package com.loanmanagement.testsupport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.loanmanagement.dao.LoanRepository;
import com.loanmanagement.model.LoanApplication;
import com.loanmanagement.model.LoanStatus;
import com.loanmanagement.service.LoanApplicationCommand;
import com.loanmanagement.service.LoanCalculation;

/** In-memory loan store for screen tests. Applicant details are placeholders; the JDBC repository joins users. */
public final class InMemoryLoanRepository implements LoanRepository {
    private final List<LoanApplication> loans = new ArrayList<>();
    private long nextId = 1;

    @Override
    public LoanApplication create(long userId, LoanApplicationCommand command, BigDecimal interestRate) {
        LoanApplication loan = new LoanApplication(nextId++, userId, "Asha", "Patel", "asha@example.com",
                "9876543210", command.amount(), command.loanType(), command.durationMonths(), interestRate,
                command.annualIncome(), command.purpose(), LoanStatus.PENDING, null, null, Instant.now(),
                null, null, null);
        loans.add(loan);
        return loan;
    }

    @Override
    public List<LoanApplication> findAll() {
        return List.copyOf(loans);
    }

    @Override
    public List<LoanApplication> findByUser(long userId) {
        return loans.stream().filter(loan -> loan.userId() == userId).toList();
    }

    @Override
    public Optional<LoanApplication> findById(long loanId) {
        return loans.stream().filter(loan -> loan.id() == loanId).findFirst();
    }

    @Override
    public void review(long adminId, long loanId, LoanStatus status, String note, LoanCalculation calculation) {
        // Review is not exercised by the user-facing screen tests.
    }
}
