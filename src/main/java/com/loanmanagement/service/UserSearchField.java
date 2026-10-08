package com.loanmanagement.service;

public enum UserSearchField {
    FIRST_NAME("first_name", false),
    LAST_NAME("last_name", false),
    EMAIL("email", false),
    MOBILE("mobile", false),
    USERNAME("username", false),
    STATUS("status", true);

    private final String column;
    private final boolean exactMatch;

    UserSearchField(String column, boolean exactMatch) {
        this.column = column;
        this.exactMatch = exactMatch;
    }

    public String column() {
        return column;
    }

    public boolean exactMatch() {
        return exactMatch;
    }
}