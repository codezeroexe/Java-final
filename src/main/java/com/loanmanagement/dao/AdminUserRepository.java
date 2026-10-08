package com.loanmanagement.dao;

import java.util.List;
import java.util.Optional;

import com.loanmanagement.model.AccountStatus;
import com.loanmanagement.model.User;
import com.loanmanagement.service.UserProfileUpdate;
import com.loanmanagement.service.UserSearchField;

public interface AdminUserRepository {
    List<User> searchUsers(UserSearchField field, String query);

    Optional<User> findActiveUser(long userId);

    void updateUser(long actorId, long userId, UserProfileUpdate update);

    void updateStatus(long actorId, long userId, AccountStatus status);

    void anonymizeUser(long actorId, long userId);
}