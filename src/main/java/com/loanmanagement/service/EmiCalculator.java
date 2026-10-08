package com.loanmanagement.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class EmiCalculator {
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final RoundingMode CURRENCY_ROUNDING = RoundingMode.HALF_UP;
    private static final int CURRENCY_SCALE = 2;
    private static final int MAX_TENURE_MONTHS = 1_200;

    public LoanCalculation calculate(BigDecimal principal, BigDecimal annualRate, int tenureMonths) {
        if (principal == null || principal.signum() <= 0) {
            throw new IllegalArgumentException("Principal must be positive");
        }
        if (annualRate == null || annualRate.signum() < 0) {
            throw new IllegalArgumentException("Interest rate must not be negative");
        }
        if (tenureMonths < 1 || tenureMonths > MAX_TENURE_MONTHS) {
            throw new IllegalArgumentException("Tenure must be between 1 and 1200 months");
        }

        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(1_200), CALCULATION_CONTEXT);
        BigDecimal emi;
        if (monthlyRate.signum() == 0) {
            emi = principal.divide(BigDecimal.valueOf(tenureMonths), CALCULATION_CONTEXT);
        } else {
            BigDecimal factor = BigDecimal.ONE.add(monthlyRate, CALCULATION_CONTEXT)
                    .pow(tenureMonths, CALCULATION_CONTEXT);
            emi = principal.multiply(monthlyRate, CALCULATION_CONTEXT)
                    .multiply(factor, CALCULATION_CONTEXT)
                    .divide(factor.subtract(BigDecimal.ONE, CALCULATION_CONTEXT), CALCULATION_CONTEXT);
        }

        BigDecimal roundedEmi = emi.setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
        BigDecimal totalPayable = roundedEmi.multiply(BigDecimal.valueOf(tenureMonths))
                .setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
        BigDecimal totalInterest = totalPayable.subtract(principal).setScale(CURRENCY_SCALE, CURRENCY_ROUNDING);
        return new LoanCalculation(roundedEmi, totalInterest, totalPayable);
    }
}