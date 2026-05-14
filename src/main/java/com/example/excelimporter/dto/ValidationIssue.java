package com.example.excelimporter.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ValidationIssue {

    private final IssueSeverity severity;
    private final int rowNumber;
    private final String fieldName;
    private final String code;
    private final String message;
}
