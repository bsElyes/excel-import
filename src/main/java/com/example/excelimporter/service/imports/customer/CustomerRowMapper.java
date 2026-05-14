package com.example.excelimporter.service.imports.customer;

import com.example.excelimporter.dto.CustomerImportRow;
import com.example.excelimporter.service.CustomerValidationRules;
import com.example.excelimporter.service.excel.ExcelRowMapper;
import com.example.excelimporter.service.excel.HeaderMapping;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.Locale;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
class CustomerRowMapper implements ExcelRowMapper<CustomerImportRow> {

    private final int businessKeyIndex;
    private final int nameIndex;
    private final int emailIndex;
    private final int amountIndex;

    static CustomerRowMapper from(HeaderMapping headerMapping) {
        return new CustomerRowMapper(
                headerMapping.requiredIndex("businessKey"),
                headerMapping.requiredIndex("name"),
                headerMapping.requiredIndex("email"),
                headerMapping.requiredIndex("amount")
        );
    }

    @Override
    public boolean isBlank(String[] values) {
        return !hasText(valueAt(values, businessKeyIndex))
                && !hasText(valueAt(values, nameIndex))
                && !hasText(valueAt(values, emailIndex))
                && !hasText(valueAt(values, amountIndex));
    }

    @Override
    public CustomerImportRow map(int rowNumber, String[] values) {
        String email = cleanText(valueAt(values, emailIndex));
        ParsedAmount parsedAmount = parseAmount(valueAt(values, amountIndex));

        return new CustomerImportRow(
                rowNumber,
                normalizedBusinessKey(valueAt(values, businessKeyIndex)),
                cleanText(valueAt(values, nameIndex)),
                email,
                CustomerValidationRules.isEmailInvalid(email),
                parsedAmount.getAmount(),
                parsedAmount.isInvalid()
        );
    }

    private String normalizedBusinessKey(String value) {
        String cleanValue = cleanText(value);
        return cleanValue == null ? null : cleanValue.toUpperCase(Locale.ENGLISH);
    }

    private ParsedAmount parseAmount(String value) {
        try {
            return ParsedAmount.valid(parseAmountValue(value));
        } catch (NumberFormatException ex) {
            return ParsedAmount.invalid();
        }
    }

    private BigDecimal parseAmountValue(String value) {
        String cleanValue = cleanText(value);
        if (cleanValue == null) {
            return null;
        }
        return new BigDecimal(cleanValue.replace(",", ""));
    }

    private String valueAt(String[] values, int index) {
        if (index >= values.length) {
            return null;
        }
        return values[index];
    }

    private String cleanText(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static class ParsedAmount {

        private final BigDecimal amount;
        private final boolean invalid;

        static ParsedAmount valid(BigDecimal amount) {
            return new ParsedAmount(amount, false);
        }

        static ParsedAmount invalid() {
            return new ParsedAmount(null, true);
        }
    }
}
