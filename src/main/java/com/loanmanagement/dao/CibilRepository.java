package com.loanmanagement.dao;

public interface CibilRepository {
    void updateCibil(long userId, String pan, int cibilScore);
}