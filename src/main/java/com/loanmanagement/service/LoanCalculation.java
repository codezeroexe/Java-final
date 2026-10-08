package com.loanmanagement.service;

import java.math.BigDecimal;

public record LoanCalculation(BigDecimal emi, BigDecimal totalInterest, BigDecimal totalPayable) {
}