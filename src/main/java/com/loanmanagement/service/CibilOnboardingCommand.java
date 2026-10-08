package com.loanmanagement.service;

import com.loanmanagement.model.LoanType;

public record CibilOnboardingCommand(
        String firstName,
        String lastName,
        String mobile,
        String email,
        String location,
        String pan,
        LoanType loanType) {
}