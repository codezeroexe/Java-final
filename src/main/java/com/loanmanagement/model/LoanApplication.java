package com.loanmanagement.model;

import java.math.BigDecimal;
import java.time.Instant;

public record LoanApplication(
        long id,
        long userId,
        String firstName,
        String lastName,
        String email,
        String mobile,
        BigDecimal loanAmount,
        LoanType loanType,
        int durationMonths,
        BigDecimal interestRate,
        BigDecimal annualIncome,
        String purpose,
        LoanStatus status,
        String rejectionReason,
        String adminNote,
        Instant appliedAt,
        BigDecimal emiAmount,
        BigDecimal totalInterest,
        BigDecimal totalPayable) {
}