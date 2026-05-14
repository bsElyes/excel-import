package com.example.excelimporter.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class ImportReport {

    private final String importId;
    private final String fileName;
    @Setter
    private ImportStatus status = ImportStatus.RUNNING;
    @Setter
    private String message;
    private int totalRows;
    private int createdRows;
    private int updatedRows;
    private int skippedRows;
    private int blockingErrorCount;
    private int warningCount;
    @Setter
    private long durationMillis;
    private final List<ValidationIssue> issues = new ArrayList<ValidationIssue>();

    public void addRows(int rows) {
        this.totalRows += rows;
    }

    public void addCreatedRows(int rows) {
        this.createdRows += rows;
    }

    public void addUpdatedRows(int rows) {
        this.updatedRows += rows;
    }

    public void addSkippedRows(int rows) {
        this.skippedRows += rows;
    }

    public void addIssues(ValidationResult validationResult) {
        this.blockingErrorCount += validationResult.getBlockingErrors().size();
        this.warningCount += validationResult.getWarnings().size();
        this.issues.addAll(validationResult.getAllIssues());
    }

    public void addIssue(ValidationIssue issue) {
        if (IssueSeverity.BLOCKING_ERROR.equals(issue.getSeverity())) {
            this.blockingErrorCount++;
        } else if (IssueSeverity.WARNING.equals(issue.getSeverity())) {
            this.warningCount++;
        }
        this.issues.add(issue);
    }

    public void addIssuePreview(ValidationIssue issue) {
        this.issues.add(issue);
    }

    public void addIssueCounts(int blockingErrorCount, int warningCount) {
        this.blockingErrorCount += blockingErrorCount;
        this.warningCount += warningCount;
    }

    public List<ValidationIssue> getIssues() {
        return Collections.unmodifiableList(issues);
    }
}
