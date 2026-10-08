package com.loanmanagement.service;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.loanmanagement.model.LoanType;

class LoanCalculationTest {
    @Test
    void calculatesEmiAndTotalsUsingCurrencyRounding() {
        LoanCalculation result = new EmiCalculator().calculate(
                new BigDecimal("100000.00"), new BigDecimal("12.00"), 12);

        assertEquals(new BigDecimal("8884.88"), result.emi());
        assertEquals(new BigDecimal("106618.56"), result.totalPayable());
        assertEquals(new BigDecimal("6618.56"), result.totalInterest());
    }

    @Test
    void handlesZeroInterestAndRejectsInvalidTenure() {
        LoanCalculation result = new EmiCalculator().calculate(
                new BigDecimal("1200.00"), BigDecimal.ZERO, 12);
        assertEquals(new BigDecimal("100.00"), result.emi());
        assertThrows(IllegalArgumentException.class, () -> new EmiCalculator().calculate(
                new BigDecimal("1200"), BigDecimal.ZERO, 0));
    }

    @Test
    void mapsCibilBandsToRatesWithinEachLoanTypeRange() {
        InterestRatePolicy policy = new InterestRatePolicy();
        assertEquals(new BigDecimal("8.50"), policy.rateFor(LoanType.HOME, 600));
        assertEquals(new BigDecimal("7.50"), policy.rateFor(LoanType.HOME, 700));
        assertEquals(new BigDecimal("6.50"), policy.rateFor(LoanType.HOME, 800));
        assertEquals(new BigDecimal("9.00"), policy.rateFor(LoanType.PERSONAL, 900));
        assertThrows(IllegalArgumentException.class, () -> policy.rateFor(LoanType.VEHICLE, 599));
    }
}