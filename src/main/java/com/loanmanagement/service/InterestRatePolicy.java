package com.loanmanagement.service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import com.loanmanagement.model.LoanType;

public final class InterestRatePolicy {
    private static final Map<LoanType, RateRange> RATES = new EnumMap<>(LoanType.class);

    static {
        RATES.put(LoanType.HOME, new RateRange("6.50", "8.50"));
        RATES.put(LoanType.PERSONAL, new RateRange("9.00", "12.00"));
        RATES.put(LoanType.EDUCATION, new RateRange("4.00", "6.00"));
        RATES.put(LoanType.VEHICLE, new RateRange("7.00", "9.00"));
    }

    public BigDecimal rateFor(LoanType loanType, int cibilScore) {
        if (cibilScore < 600 || cibilScore > 900) {
            throw new IllegalArgumentException("CIBIL score must be between 600 and 900");
        }

        RateRange range = RATES.get(loanType);
        if (range == null) {
            throw new IllegalArgumentException("Loan type is required");
        }
        if (cibilScore < 700) {
            return range.maximum();
        }
        if (cibilScore < 800) {
            return range.minimum().add(range.maximum()).divide(BigDecimal.valueOf(2));
        }
        return range.minimum();
    }

    private record RateRange(BigDecimal minimum, BigDecimal maximum) {
        private RateRange(String minimum, String maximum) {
            this(new BigDecimal(minimum), new BigDecimal(maximum));
        }
    }
}