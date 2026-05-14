package com.example.excelimporter.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationResult {

    private final List<ValidationIssue> blockingErrors = new ArrayList<ValidationIssue>();
    private final List<ValidationIssue> warnings = new ArrayList<ValidationIssue>();

    public void addBlockingError(int rowNumber, String fieldName, String code, String message) {
        blockingErrors.add(new ValidationIssue(IssueSeverity.BLOCKING_ERROR, rowNumber, fieldName, code, message));
    }

    public void addWarning(int rowNumber, String fieldName, String code, String message) {
        warnings.add(new ValidationIssue(IssueSeverity.WARNING, rowNumber, fieldName, code, message));
    }

    public boolean hasBlockingErrors() {
        return !blockingErrors.isEmpty();
    }

    public List<ValidationIssue> getBlockingErrors() {
        return Collections.unmodifiableList(blockingErrors);
    }

    public List<ValidationIssue> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    public List<ValidationIssue> getAllIssues() {
        List<ValidationIssue> issues = new ArrayList<ValidationIssue>();
        issues.addAll(blockingErrors);
        issues.addAll(warnings);
        return issues;
    }
}
