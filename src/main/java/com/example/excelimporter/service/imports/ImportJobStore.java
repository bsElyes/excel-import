package com.example.excelimporter.service.imports;

import com.example.excelimporter.dto.ImportReport;
import com.example.excelimporter.dto.ImportStatus;
import com.example.excelimporter.dto.ValidationIssue;

import java.util.List;
import java.util.Optional;

public interface ImportJobStore {

    void createJob(String importId, String importType, String fileName, ImportStatus status);

    void markRunning(String importId);

    void saveReport(String importId, ImportReport report);

    void saveReport(String importId, ImportReport report, boolean replaceIssues);

    Optional<ImportReport> findReport(String importId, int issuePreviewLimit);

    List<ValidationIssue> findIssuePreview(String importId, int issuePreviewLimit);

    List<ValidationIssue> findIssues(String importId, int limit, int offset);

    void replaceIssues(String importId, List<ValidationIssue> issues);

    void appendIssues(String importId, List<ValidationIssue> issues);
}
