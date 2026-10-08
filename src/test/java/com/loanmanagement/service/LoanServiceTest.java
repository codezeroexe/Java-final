package com.loanmanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loanmanagement.dao.LoanRepository;
import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.LoanApplication;
import com.loanmanagement.model.LoanStatus;
import com.loanmanagement.model.LoanType;
import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;
import com.loanmanagement.notification.EmailSender;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LoanServiceTest {
    @Test
    void submitsPendingLoanUsingCibilRateAndSendsNotice() {
        RecordingLoanRepository loans = new RecordingLoanRepository();
        boolean[] sent = { false };
        EmailSender emailSender = (recipient, subject, body) -> sent[0] = true;
        LoanService service = new LoanService(loans, new InterestRatePolicy(), new EmiCalculator(), emailSender);

        LoanSubmissionResult result = service.apply(activeUser(720),
                new LoanApplicationCommand(new BigDecimal("50000"), LoanType.HOME, 24,
                        new BigDecimal("900000"), "Home repair"));

        assertEquals(LoanStatus.PENDING, result.application().status());
        assertEquals(new BigDecimal("7.50"), loans.appliedRate);
        assertTrue(result.notificationSent());
        assertTrue(sent[0]);
    }

    @Test
    void requiresCibilBeforeLoanSubmissionAndRejectsNonPositiveAmount() {
        LoanService service = new LoanService(new RecordingLoanRepository(), new InterestRatePolicy(),
                new EmiCalculator(), (recipient, subject, body) -> { });
        LoanApplicationCommand command = new LoanApplicationCommand(new BigDecimal("100"), LoanType.PERSONAL,
                12, new BigDecimal("1000"), "Purpose");

        assertThrows(IllegalStateException.class, () -> service.apply(activeUser(null), command));
        assertThrows(IllegalArgumentException.class, () -> service.apply(activeUser(700),
                new LoanApplicationCommand(BigDecimal.ZERO, LoanType.PERSONAL, 12,
                        new BigDecimal("1000"), "Purpose")));
    }

    private User activeUser(Integer cibil) {
        return new User(1, "Asha", "Patel", "9876543210", "asha@example.com", "Pune", "asha",
                "hash", false, UserRole.ROLE_USER, AccountStatus.ACTIVE, 0, "ABCDE1234F", cibil,
                Instant.now(), Instant.now());
    }

    private static final class RecordingLoanRepository implements LoanRepository {
        private BigDecimal appliedRate;

        @Override
        public LoanApplication create(long userId, LoanApplicationCommand command, BigDecimal interestRate) {
            appliedRate = interestRate;
            return new LoanApplication(1, userId, "Asha", "Patel", "asha@example.com", "9876543210",
                    command.amount(), command.loanType(), command.durationMonths(), interestRate,
                    command.annualIncome(), command.purpose(), LoanStatus.PENDING, null, null, Instant.now(),
                    null, null, null);
        }

        @Override public List<LoanApplication> findAll() { return List.of(); }
        @Override public List<LoanApplication> findByUser(long userId) { return List.of(); }
        @Override public Optional<LoanApplication> findById(long loanId) { return Optional.empty(); }
        @Override public void review(long adminId, long loanId, LoanStatus status, String note,
                LoanCalculation calculation) { }
    }
}