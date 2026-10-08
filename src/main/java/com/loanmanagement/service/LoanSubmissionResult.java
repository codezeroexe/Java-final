package com.loanmanagement.service;

import com.loanmanagement.model.LoanApplication;

public record LoanSubmissionResult(LoanApplication application, boolean notificationSent) {
}