package com.loanmanagement.tui.account;

import com.loanmanagement.model.User;
import com.loanmanagement.model.UserRole;

/** The signed-in user and the facts the status line shows. Screens update it after changes. */
public final class Session {
    private User user;
    private int loanCount;

    public Session(User user) {
        this.user = user;
    }

    public User user() {
        return user;
    }

    public void update(User updated) {
        this.user = updated;
    }

    public int loanCount() {
        return loanCount;
    }

    public void setLoanCount(int loanCount) {
        this.loanCount = loanCount;
    }

    /** One line for the status row, for example "asha · ROLE_USER · CIBIL 742 · 3 loans". */
    public String statusLine() {
        if (user.role() == UserRole.ROLE_ADMIN) {
            return user.username() + "  ·  " + user.role();
        }
        String cibil = user.cibilScore() == null ? "CIBIL not assessed" : "CIBIL " + user.cibilScore();
        return user.username() + "  ·  " + user.role() + "  ·  " + cibil + "  ·  " + loanCount + " loans";
    }
}
