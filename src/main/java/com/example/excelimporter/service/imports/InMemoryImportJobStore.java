package com.example.excelimporter.service.imports;

import com.example.excelimporter.dto.ImportReport;
import com.example.excelimporter.dto.ImportStatus;
import com.example.excelimporter.dto.ValidationIssue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@ConditionalOnProperty(name = "app.import.job-store", havingValue = "memory")
public class InMemoryImportJobStore implements ImportJobStore {

    private final ConcurrentMap<String, ImportReport> reports = new ConcurrentHashMap<String, ImportReport>();
    private final ConcurrentMap<String, List<ValidationIssue>> issues = new ConcurrentHashMap<String, List<ValidationIssue>>();

    public void createJob(String importId, String importType, String fileName, ImportStatus status) {
        ImportReport report = new ImportReport(importId, fileName);
        report.setStatus(status);
        reports.put(importId, report);
        issues.put(importId, new ArrayList<ValidationIssue>());
    }

    public void markRunning(String importId) {
        ImportReport report = reports.get(importId);
        if (report != null) {
            report.setStatus(ImportStatus.RUNNING);
        }
    }

    public void saveReport(String importId, ImportReport report) { saveReport(importId, report, true); }

    public void saveReport(String importId, ImportReport report, boolean replaceIssues) {
        reports.put(importId, cloneReport(report));
        if (replaceIssues) {
            replaceIssues(importId, report.getIssues());
        }
    }

    public Optional<ImportReport> findReport(String importId, int issuePreviewLimit) {
        ImportReport report = reports.get(importId);
        if (report == null) {
            return Optional.empty();
        }
        ImportReport copy = cloneReport(report);
        for (ValidationIssue issue : findIssuePreview(importId, issuePreviewLimit)) {
            copy.addIssuePreview(issue);
        }
        return Optional.of(copy);
    }

    public List<ValidationIssue> findIssuePreview(String importId, int issuePreviewLimit) { return findIssues(importId, issuePreviewLimit, 0); }

    public List<ValidationIssue> findIssues(String importId, int limit, int offset) {
        List<ValidationIssue> all = issues.get(importId);
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, limit);
        if (safeOffset >= all.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(all.size(), safeOffset + safeLimit);
        return new ArrayList<ValidationIssue>(all.subList(safeOffset, toIndex));
    }

    public void replaceIssues(String importId, List<ValidationIssue> newIssues) {
        issues.put(importId, new ArrayList<ValidationIssue>(newIssues));
    }

    public void appendIssues(String importId, List<ValidationIssue> newIssues) {
        issues.compute(importId, (key, existing) -> {
            List<ValidationIssue> merged = existing == null ? new ArrayList<ValidationIssue>() : new ArrayList<ValidationIssue>(existing);
            merged.addAll(newIssues);
            return merged;
        });
    }

    private ImportReport cloneReport(ImportReport source) {
        ImportReport target = new ImportReport(source.getImportId(), source.getFileName());
        target.setStatus(source.getStatus());
        target.setMessage(source.getMessage());
        target.addRows(source.getTotalRows());
        target.addCreatedRows(source.getCreatedRows());
        target.addUpdatedRows(source.getUpdatedRows());
        target.addSkippedRows(source.getSkippedRows());
        target.addIssueCounts(source.getBlockingErrorCount(), source.getWarningCount());
        target.setDurationMillis(source.getDurationMillis());
        return target;
    }
}
