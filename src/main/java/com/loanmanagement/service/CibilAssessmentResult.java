package com.loanmanagement.service;

import com.loanmanagement.model.User;
import java.math.BigDecimal;

public record CibilAssessmentResult(User user, BigDecimal interestRate) {
}