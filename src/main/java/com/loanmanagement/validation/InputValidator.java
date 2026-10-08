package com.loanmanagement.validation;

import java.util.regex.Pattern;

public final class InputValidator {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern MOBILE = Pattern.compile("^\\d{10}$");
    private static final Pattern NAME = Pattern.compile("^[\\p{L}]+(?:[ '-][\\p{L}]+)*$");
    private static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");

    private InputValidator() {
    }

    public static boolean isValidEmail(String value) {
        return value != null && EMAIL.matcher(value.trim()).matches();
    }

    public static boolean isValidMobile(String value) {
        return value != null && MOBILE.matcher(value.trim()).matches();
    }

    public static boolean isValidName(String value) {
        return value != null && NAME.matcher(value.trim()).matches();
    }

    public static boolean isAlphabeticName(String value) {
        return value != null && value.trim().matches("^[\\p{L}]+$");
    }

    public static boolean isValidPassword(String value) {
        return value != null
                && value.length() >= 8
                && value.matches(".*[A-Z].*")
                && value.matches(".*[a-z].*")
                && value.matches(".*\\d.*")
                && value.matches(".*[^A-Za-z0-9].*");
    }

    public static boolean isValidPan(String value) {
        return value != null && PAN.matcher(value.trim()).matches();
    }
}