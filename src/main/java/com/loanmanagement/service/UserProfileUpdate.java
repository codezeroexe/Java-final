package com.loanmanagement.service;

public record UserProfileUpdate(
        String firstName,
        String lastName,
        String mobile,
        String email,
        String location) {
}