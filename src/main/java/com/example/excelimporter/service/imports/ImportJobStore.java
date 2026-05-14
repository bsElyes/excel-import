package com.example.excelimporter.service.imports;

import com.example.excelimporter.dto.ImportReport;
import com.example.excelimporter.dto.ImportStatus;
import com.example.excelimporter.dto.IssueSeverity;
import com.example.excelimporter.dto.ValidationIssue;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImportJobStore {

    private final JdbcTemplate jdbcTemplate;

    public void createJob(String importId, String importType, String fileName, ImportStatus status) {
        Instant now = Instant.now();
        jdbcTemplate.update(
                "insert into import_jobs "
                        + "(import_id, import_type, file_name, status, created_at, updated_at) "
                        + "values (?, ?, ?, ?, ?, ?)",
                importId,
                importType,
                fileName,
                status.name(),
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    public void markRunning(String importId) {
        jdbcTemplate.update(
                "update import_jobs set status = ?, updated_at = ? where import_id = ?",
                ImportStatus.RUNNING.name(),
                Timestamp.from(Instant.now()),
                importId
        );
    }

    public void saveReport(String importId, ImportReport report) {
        saveReport(importId, report, true);
    }

    public void saveReport(String importId, ImportReport report, boolean replaceIssues) {
        jdbcTemplate.update(
                "update import_jobs set status = ?, message = ?, total_rows = ?, created_rows = ?, "
                        + "updated_rows = ?, skipped_rows = ?, blocking_error_count = ?, warning_count = ?, "
                        + "duration_millis = ?, updated_at = ? where import_id = ?",
                report.getStatus().name(),
                report.getMessage(),
                report.getTotalRows(),
                report.getCreatedRows(),
                report.getUpdatedRows(),
                report.getSkippedRows(),
                report.getBlockingErrorCount(),
                report.getWarningCount(),
                report.getDurationMillis(),
                Timestamp.from(Instant.now()),
                importId
        );
        if (replaceIssues) {
            replaceIssues(importId, report.getIssues());
        }
    }

    public Optional<ImportReport> findReport(String importId, int issuePreviewLimit) {
        List<ImportReport> reports = jdbcTemplate.query(
                "select * from import_jobs where import_id = ?",
                ps -> ps.setString(1, importId),
                (rs, rowNum) -> {
                    ImportReport report = new ImportReport(rs.getString("import_id"), rs.getString("file_name"));
                    report.setStatus(ImportStatus.valueOf(rs.getString("status")));
                    report.setMessage(rs.getString("message"));
                    report.addRows(rs.getInt("total_rows"));
                    report.addCreatedRows(rs.getInt("created_rows"));
                    report.addUpdatedRows(rs.getInt("updated_rows"));
                    report.addSkippedRows(rs.getInt("skipped_rows"));
                    report.addIssueCounts(rs.getInt("blocking_error_count"), rs.getInt("warning_count"));
                    report.setDurationMillis(rs.getLong("duration_millis"));
                    return report;
                }
        );

        if (reports.isEmpty()) {
            return Optional.empty();
        }

        ImportReport report = reports.get(0);
        for (ValidationIssue issue : findIssuePreview(importId, issuePreviewLimit)) {
            report.addIssuePreview(issue);
        }
        return Optional.of(report);
    }

    public List<ValidationIssue> findIssuePreview(String importId, int issuePreviewLimit) {
        return findIssues(importId, issuePreviewLimit, 0);
    }

    public List<ValidationIssue> findIssues(String importId, int limit, int offset) {
        return jdbcTemplate.query(
                "select severity, row_number, field_name, code, message "
                        + "from import_issues where import_id = ? order by row_number limit ? offset ?",
                ps -> {
                    ps.setString(1, importId);
                    ps.setInt(2, Math.max(1, limit));
                    ps.setInt(3, Math.max(0, offset));
                },
                (rs, rowNum) -> new ValidationIssue(
                        IssueSeverity.valueOf(rs.getString("severity")),
                        rs.getInt("row_number"),
                        rs.getString("field_name"),
                        rs.getString("code"),
                        rs.getString("message")
                )
        );
    }

    public void replaceIssues(String importId, List<ValidationIssue> issues) {
        jdbcTemplate.update("delete from import_issues where import_id = ?", importId);
        appendIssues(importId, issues);
    }

    public void appendIssues(String importId, final List<ValidationIssue> issues) {
        if (issues.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                "insert into import_issues (import_id, severity, row_number, field_name, code, message) "
                        + "values (?, ?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int index) throws SQLException {
                        ValidationIssue issue = issues.get(index);
                        ps.setString(1, importId);
                        ps.setString(2, issue.getSeverity().name());
                        ps.setInt(3, issue.getRowNumber());
                        ps.setString(4, issue.getFieldName());
                        ps.setString(5, issue.getCode());
                        ps.setString(6, issue.getMessage());
                    }

                    @Override
                    public int getBatchSize() {
                        return issues.size();
                    }
                }
        );
    }
}
