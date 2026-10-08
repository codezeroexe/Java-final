package com.loanmanagement.service;

public record RegisterCommand(
        String firstName,
        String lastName,
        String mobile,
        String email,
        String location,
        String username,
        char[] password) {
}