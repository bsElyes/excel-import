package com.example.excelimporter.service.imports;

import com.example.excelimporter.dto.ValidationIssue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class StagedImportValidation {

    private final int totalRows;
    private final int createdRows;
    private final int updatedRows;
    private final int skippedRows;
    private final int blockingErrorCount;
    private final int warningCount;
    private final List<ValidationIssue> issuePreview;
}
