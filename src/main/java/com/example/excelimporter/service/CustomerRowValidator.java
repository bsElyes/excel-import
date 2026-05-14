package com.example.excelimporter.service;

import com.example.excelimporter.dto.CustomerImportRow;
import com.example.excelimporter.dto.ValidationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CustomerRowValidator {

    public ValidationResult validate(CustomerImportRow row, boolean existingRecord, boolean duplicateInFile) {
        ValidationResult result = new ValidationResult();

        if (CustomerValidationRules.isBlank(row.getBusinessKey())) {
            result.addBlockingError(row.getRowNumber(), "businessKey", "BUSINESS_KEY_REQUIRED", "Business key is required.");
        }

        if (duplicateInFile) {
            result.addBlockingError(row.getRowNumber(), "businessKey", "DUPLICATE_IN_FILE", "Business key appears more than once in this file.");
        }

        if (CustomerValidationRules.isBlank(row.getName())) {
            result.addBlockingError(row.getRowNumber(), "name", "NAME_REQUIRED", "Name is required.");
        }

        if (CustomerValidationRules.isBlank(row.getEmail())) {
            result.addBlockingError(row.getRowNumber(), "email", "EMAIL_REQUIRED", "Email is required.");
        } else if (row.isEmailInvalid()) {
            result.addBlockingError(row.getRowNumber(), "email", "EMAIL_INVALID", "Email format is invalid.");
        }

        if (row.isAmountInvalid()) {
            result.addBlockingError(row.getRowNumber(), "amount", "AMOUNT_INVALID", "Amount must be numeric.");
        } else if (row.getAmount() == null) {
            result.addBlockingError(row.getRowNumber(), "amount", "AMOUNT_REQUIRED", "Amount is required.");
        } else if (row.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            result.addBlockingError(row.getRowNumber(), "amount", "AMOUNT_NEGATIVE", "Amount cannot be negative.");
        } else if (CustomerValidationRules.isLargeAmount(row.getAmount())) {
            result.addWarning(row.getRowNumber(), "amount", "AMOUNT_LARGE", "Amount is unusually large and should be reviewed.");
        }

        if (existingRecord) {
            result.addWarning(row.getRowNumber(), "businessKey", "EXISTING_RECORD", "Existing database record will be updated.");
        }

        return result;
    }
}
