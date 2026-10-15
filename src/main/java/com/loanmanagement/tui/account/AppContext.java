package com.loanmanagement.tui.account;

import com.loanmanagement.service.AdminUserService;
import com.loanmanagement.service.AuthenticationService;
import com.loanmanagement.service.CibilService;
import com.loanmanagement.service.LoanService;
import com.loanmanagement.service.NotificationService;
import com.loanmanagement.service.PasswordResetService;

/** Services the screens call. Built once at launch, the same way {@code Main} builds them. */
public record AppContext(
        AuthenticationService authentication,
        PasswordResetService passwordResets,
        CibilService cibil,
        LoanService loans,
        AdminUserService adminUsers,
        NotificationService notifications) {
}
