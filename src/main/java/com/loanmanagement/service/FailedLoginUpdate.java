package com.loanmanagement.service;

import com.loanmanagement.model.AccountStatus;

public record FailedLoginUpdate(AccountStatus accountStatus, int failedAttempts) {
}