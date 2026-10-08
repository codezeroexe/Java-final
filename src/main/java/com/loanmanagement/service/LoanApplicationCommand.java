package com.loanmanagement.service;

import com.loanmanagement.model.LoanType;
import java.math.BigDecimal;

public record LoanApplicationCommand(
        BigDecimal amount,
        LoanType loanType,
        int durationMonths,
        BigDecimal annualIncome,
        String purpose) {
}