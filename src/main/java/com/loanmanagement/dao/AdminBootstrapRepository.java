package com.loanmanagement.dao;

public interface AdminBootstrapRepository {
    void createAdmin(String username, String email, String passwordHash);
}