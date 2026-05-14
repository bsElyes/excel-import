package com.example.excelimporter.service;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public final class CustomerValidationRules {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final BigDecimal LARGE_AMOUNT_WARNING_LIMIT = new BigDecimal("10000.00");

    private CustomerValidationRules() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isEmailInvalid(String email) {
        return !isBlank(email) && !EMAIL_PATTERN.matcher(email).matches();
    }

    public static boolean isLargeAmount(BigDecimal amount) {
        return amount != null && amount.compareTo(LARGE_AMOUNT_WARNING_LIMIT) > 0;
    }
}
