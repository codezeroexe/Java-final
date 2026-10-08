package com.loanmanagement.dao;

import com.loanmanagement.model.LoanApplication;
import com.loanmanagement.model.LoanStatus;
import com.loanmanagement.service.LoanApplicationCommand;
import com.loanmanagement.service.LoanCalculation;
import java.util.List;
import java.util.Optional;

public interface LoanRepository {
    LoanApplication create(long userId, LoanApplicationCommand command, java.math.BigDecimal interestRate);

    List<LoanApplication> findAll();

    List<LoanApplication> findByUser(long userId);

    Optional<LoanApplication> findById(long loanId);

    void review(long adminId, long loanId, LoanStatus status, String note, LoanCalculation calculation);
}