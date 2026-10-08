package com.loanmanagement.tui.account;

import com.loanmanagement.service.AuthenticationService;
import com.loanmanagement.service.CibilService;
import com.loanmanagement.service.PasswordResetService;

/** Services the account screens call. Built once at launch, the same way {@code Main} builds them. */
public record AppContext(
        AuthenticationService authentication,
        PasswordResetService passwordResets,
        CibilService cibil) {
}
