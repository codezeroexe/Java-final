package com.loanmanagement.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class InputValidatorTest {
    @Test
    void validatesRegistrationFields() {
        assertTrue(InputValidator.isValidEmail("user@example.com"));
        assertFalse(InputValidator.isValidEmail("invalid-email"));
        assertTrue(InputValidator.isValidMobile("9876543210"));
        assertFalse(InputValidator.isValidMobile("12345"));
        assertTrue(InputValidator.isValidName("Asha Patel"));
        assertFalse(InputValidator.isValidName("Asha42"));
        assertTrue(InputValidator.isValidPassword("Strong!Pass1"));
        assertFalse(InputValidator.isValidPassword("weakpass"));
    }

    @Test
    void validatesPanFormat() {
        assertTrue(InputValidator.isValidPan("ABCDE1234F"));
        assertFalse(InputValidator.isValidPan("ABCD1234F"));
        assertFalse(InputValidator.isValidPan("ABCDE1234f"));
    }
}