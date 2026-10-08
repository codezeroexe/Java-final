package com.loanmanagement.service;

import com.loanmanagement.model.User;
import java.math.BigDecimal;

public record CibilOnboardingResult(
        User user,
        BigDecimal interestRate,
        boolean credentialsEmailSent,
        String recoveryPassword) {
}