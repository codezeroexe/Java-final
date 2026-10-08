package com.loanmanagement.dao;

import com.loanmanagement.db.ConnectionFactory;
import com.loanmanagement.model.LoanApplication;
import com.loanmanagement.model.LoanStatus;
import com.loanmanagement.model.LoanType;
import com.loanmanagement.service.LoanApplicationCommand;
import com.loanmanagement.service.LoanCalculation;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcLoanRepository implements LoanRepository {
    private static final String LOAN_COLUMNS = "l.id, l.user_id, u.first_name, u.last_name, u.email, u.mobile, "
            + "l.loan_amount, l.loan_type, l.loan_duration_months, l.interest_rate, l.annual_income, l.purpose, "
            + "l.status, l.rejection_reason, l.admin_note, l.applied_at, l.emi_amount, l.total_interest, l.total_payable";

    private final ConnectionFactory connections;

    public JdbcLoanRepository(ConnectionFactory connections) {
        this.connections = connections;
    }

    @Override
    public LoanApplication create(long userId, LoanApplicationCommand command, java.math.BigDecimal interestRate) {
        String sql = "INSERT INTO loan_applications (user_id, loan_amount, loan_type, loan_duration_months, "
                + "interest_rate, annual_income, purpose, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDING')";
        try (Connection connection = connections.open();
                PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, userId);
            statement.setBigDecimal(2, command.amount());
            statement.setString(3, command.loanType().name());
            statement.setInt(4, command.durationMonths());
            statement.setBigDecimal(5, interestRate);
            statement.setBigDecimal(6, command.annualIncome());
            statement.setString(7, command.purpose().trim());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Database did not return generated user ID");
                }
                return findById(connection, keys.getLong(1));
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not submit loan application", exception);
        }
    }

    @Override
    public List<LoanApplication> findAll() {
        return query("SELECT " + LOAN_COLUMNS + " FROM loan_applications l "
                + "JOIN users u ON u.id = l.user_id ORDER BY l.applied_at DESC");
    }

    @Override
    public List<LoanApplication> findByUser(long userId) {
        return query("SELECT " + LOAN_COLUMNS + " FROM loan_applications l "
                + "JOIN users u ON u.id = l.user_id WHERE l.user_id = ? ORDER BY l.applied_at DESC", userId);
    }

    @Override
    public Optional<LoanApplication> findById(long loanId) {
        List<LoanApplication> found = query("SELECT " + LOAN_COLUMNS + " FROM loan_applications l "
                + "JOIN users u ON u.id = l.user_id WHERE l.id = ?", loanId);
        return found.stream().findFirst();
    }

    @Override
    public void review(long adminId, long loanId, LoanStatus status, String note, LoanCalculation calculation) {
        String sql = "UPDATE loan_applications SET status = ?, rejection_reason = ?, admin_note = ?, "
                + "reviewed_at = CURRENT_TIMESTAMP(6), reviewed_by = ?, emi_amount = ?, total_interest = ?, "
                + "total_payable = ? WHERE id = ? AND (status = 'PENDING' OR status = 'NEEDS_MORE_INFO')";
        try (Connection connection = connections.open()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, status.name());
                statement.setString(2, status == LoanStatus.REJECTED ? note : null);
                statement.setString(3, status == LoanStatus.NEEDS_MORE_INFO ? note : null);
                statement.setLong(4, adminId);
                statement.setBigDecimal(5, calculation == null ? null : calculation.emi());
                statement.setBigDecimal(6, calculation == null ? null : calculation.totalInterest());
                statement.setBigDecimal(7, calculation == null ? null : calculation.totalPayable());
                statement.setLong(8, loanId);
                if (statement.executeUpdate() == 0) {
                    throw new IllegalStateException("Loan was not found or is already in a final state");
                }
                try (PreparedStatement audit = connection.prepareStatement(
                        "INSERT INTO admin_audit_log (actor_user_id, action, target_loan_id, details) "
                                + "VALUES (?, ?, ?, ?)")) {
                    audit.setLong(1, adminId);
                    audit.setString(2, "LOAN_" + status.name());
                    audit.setLong(3, loanId);
                    audit.setString(4, note);
                    audit.executeUpdate();
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not review loan", exception);
        }
    }

    private List<LoanApplication> query(String sql, Long... parameters) {
        try (Connection connection = connections.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setLong(index + 1, parameters[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                List<LoanApplication> loans = new ArrayList<>();
                while (result.next()) {
                    loans.add(mapLoan(result));
                }
                return loans;
            }
        } catch (SQLException exception) {
            throw new RepositoryException("Could not load loan applications", exception);
        }
    }

    private LoanApplication findById(Connection connection, long loanId) throws SQLException {
        String sql = "SELECT " + LOAN_COLUMNS + " FROM loan_applications l JOIN users u ON u.id = l.user_id "
                + "WHERE l.id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, loanId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("Created loan application could not be reloaded");
                }
                return mapLoan(result);
            }
        }
    }

    private LoanApplication mapLoan(ResultSet result) throws SQLException {
        Timestamp appliedAt = result.getTimestamp("applied_at");
        return new LoanApplication(
                result.getLong("id"), result.getLong("user_id"), result.getString("first_name"),
                result.getString("last_name"), result.getString("email"), result.getString("mobile"),
                result.getBigDecimal("loan_amount"), LoanType.valueOf(result.getString("loan_type")),
                result.getInt("loan_duration_months"), result.getBigDecimal("interest_rate"),
                result.getBigDecimal("annual_income"), result.getString("purpose"),
                LoanStatus.valueOf(result.getString("status")), result.getString("rejection_reason"),
                result.getString("admin_note"), appliedAt == null ? null : appliedAt.toInstant(),
                result.getBigDecimal("emi_amount"), result.getBigDecimal("total_interest"),
                result.getBigDecimal("total_payable"));
    }
}