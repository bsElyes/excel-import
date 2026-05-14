package com.example.excelimporter.service.imports.customer;

import com.example.excelimporter.dto.CustomerImportRow;
import com.example.excelimporter.dto.ValidationResult;
import com.example.excelimporter.service.CustomerRowValidator;
import com.example.excelimporter.service.excel.ExcelRowMapper;
import com.example.excelimporter.service.excel.HeaderMapping;
import com.example.excelimporter.service.imports.ExcelImportDefinition;
import com.example.excelimporter.service.imports.ImportJobStore;
import com.example.excelimporter.service.imports.StagedImportValidation;
import com.example.excelimporter.service.imports.StagingExcelImportDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CustomerExcelImportDefinition implements StagingExcelImportDefinition<CustomerImportRow> {

    private static final String IMPORT_TYPE = "customers";
    private static final Collection<String> REQUIRED_HEADERS = Arrays.asList(
            "businessKey",
            "name",
            "email",
            "amount"
    );

    private final CustomerRowValidator customerRowValidator;
    private final CustomerRecordJdbcBulkWriter customerRecordJdbcBulkWriter;
    private final ImportJobStore importJobStore;

    @Override
    public String getImportType() {
        return IMPORT_TYPE;
    }

    @Override
    public Collection<String> getRequiredHeaders() {
        return REQUIRED_HEADERS;
    }

    @Override
    public ExcelRowMapper<CustomerImportRow> createRowMapper(HeaderMapping headerMapping) {
        return CustomerRowMapper.from(headerMapping);
    }

    @Override
    public String businessKey(CustomerImportRow row) {
        return row.getBusinessKey();
    }

    @Override
    public Set<String> findExistingBusinessKeys(Collection<String> businessKeys) {
        return customerRecordJdbcBulkWriter.findExistingBusinessKeys(businessKeys);
    }

    @Override
    public ValidationResult validate(CustomerImportRow row, boolean existingRecord, boolean duplicateInFile) {
        return customerRowValidator.validate(row, existingRecord, duplicateInFile);
    }

    @Override
    public void bulkWrite(List<CustomerImportRow> rows, Set<String> existingBusinessKeys, String importId) {
        customerRecordJdbcBulkWriter.bulkWrite(rows, existingBusinessKeys, importId);
    }

    @Override
    public void prepareStaging(String importId) {
        customerRecordJdbcBulkWriter.prepareStaging(importId);
    }

    @Override
    public void stageRows(String importId, List<CustomerImportRow> rows) {
        customerRecordJdbcBulkWriter.stageRows(importId, rows);
    }

    @Override
    public StagedImportValidation validateStagedRows(String importId, int issuePreviewLimit) {
        customerRecordJdbcBulkWriter.validateStaging(importId);
        int totalRows = customerRecordJdbcBulkWriter.countStagedRows(importId);
        int updatedRows = customerRecordJdbcBulkWriter.countExistingStagedRows(importId);
        int blockingErrorCount = customerRecordJdbcBulkWriter.countBlockingIssues(importId);
        int warningCount = customerRecordJdbcBulkWriter.countWarnings(importId);
        int skippedRows = customerRecordJdbcBulkWriter.countRowsWithBlockingIssues(importId);
        return new StagedImportValidation(
                totalRows,
                totalRows - updatedRows,
                updatedRows,
                skippedRows,
                blockingErrorCount,
                warningCount,
                importJobStore.findIssuePreview(importId, issuePreviewLimit)
        );
    }

    @Override
    public void mergeStagedRows(String importId) {
        customerRecordJdbcBulkWriter.mergeStagedRows(importId);
    }

    @Override
    public void clearStaging(String importId) {
        customerRecordJdbcBulkWriter.clearStaging(importId);
    }
}
