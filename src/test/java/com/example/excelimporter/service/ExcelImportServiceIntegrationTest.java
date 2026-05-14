package com.example.excelimporter.service;

import com.example.excelimporter.domain.CustomerRecord;
import com.example.excelimporter.dto.ImportReport;
import com.example.excelimporter.dto.ImportStatus;
import com.example.excelimporter.dto.ValidationIssue;
import com.example.excelimporter.repository.CustomerRecordRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.import.in-memory-row-threshold=2",
        "app.import.issue-preview-limit=50",
        "app.security.import-password=test-password"
})
class ExcelImportServiceIntegrationTest {

    @Autowired
    private ExcelImportService excelImportService;

    @Autowired
    private CustomerRecordRepository customerRecordRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from customer_import_staging");
        jdbcTemplate.update("delete from import_issues");
        jdbcTemplate.update("delete from import_jobs");
        customerRecordRepository.deleteAll();
    }

    @Test
    void streamsValidatesAndWritesCustomersThroughJdbcBatches() throws IOException {
        ImportReport firstReport = excelImportService.importCustomers(firstWorkbook(), "customers-1.xlsx");

        assertThat(firstReport.getStatus()).isEqualTo(ImportStatus.FAILED_VALIDATION);
        assertThat(firstReport.getTotalRows()).isEqualTo(3);
        assertThat(firstReport.getCreatedRows()).isZero();
        assertThat(firstReport.getUpdatedRows()).isZero();
        assertThat(firstReport.getSkippedRows()).isEqualTo(3);
        assertThat(customerRecordRepository.count()).isZero();

        ImportReport secondReport = excelImportService.importCustomers(secondWorkbook(), "customers-2.xlsx");

        assertThat(secondReport.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(secondReport.getTotalRows()).isEqualTo(2);
        assertThat(secondReport.getCreatedRows()).isEqualTo(2);
        assertThat(secondReport.getUpdatedRows()).isZero();
        assertThat(secondReport.getSkippedRows()).isZero();

        ImportReport thirdReport = excelImportService.importCustomers(updateWorkbook(), "customers-3.xlsx");

        assertThat(thirdReport.getStatus()).isEqualTo(ImportStatus.COMPLETED);
        assertThat(thirdReport.getCreatedRows()).isZero();
        assertThat(thirdReport.getUpdatedRows()).isEqualTo(1);

        Map<String, CustomerRecord> records = customerRecordRepository.findAllByBusinessKeyIn(Arrays.asList("C-001", "C-003"))
                .stream()
                .collect(Collectors.toMap(CustomerRecord::getBusinessKey, record -> record));

        assertThat(records).containsOnlyKeys("C-001", "C-003");
        assertThat(records.get("C-001").getName()).isEqualTo("Ada Updated");
        assertThat(records.get("C-001").getAmount()).isEqualByComparingTo(new BigDecimal("777.00"));
        assertThat(records.get("C-003").getEmail()).isEqualTo("katherine@example.com");
    }

    @Test
    void stagedValidationUsesTheSameEmailRulesAndCleansStaging() throws IOException {
        ImportReport report = excelImportService.importCustomers(stagedInvalidEmailWorkbook(), "invalid-email.xlsx");

        assertThat(report.getStatus()).isEqualTo(ImportStatus.FAILED_VALIDATION);
        assertThat(report.getTotalRows()).isEqualTo(3);
        assertThat(report.getSkippedRows()).isEqualTo(1);
        assertThat(report.getIssues())
                .extracting(ValidationIssue::getCode)
                .contains("EMAIL_INVALID");
        assertThat(stagedRows(report.getImportId())).isZero();
        assertThat(customerRecordRepository.count()).isZero();
    }

    @Test
    void inMemoryValidationFlagsAllDuplicateBusinessKeysLikeStaging() throws IOException {
        ImportReport report = excelImportService.importCustomers(inMemoryDuplicateWorkbook(), "duplicate-small.xlsx");

        assertThat(report.getStatus()).isEqualTo(ImportStatus.FAILED_VALIDATION);
        assertThat(report.getTotalRows()).isEqualTo(2);
        assertThat(report.getSkippedRows()).isEqualTo(2);
        assertThat(report.getBlockingErrorCount()).isEqualTo(2);
        assertThat(customerRecordRepository.count()).isZero();
    }

    private ByteArrayInputStream firstWorkbook() throws IOException {
        return workbook(Arrays.asList(
                Arrays.asList("email", "amount", "business key", "name"),
                Arrays.asList("ada@example.com", "1,234.50", " c-001 ", " Ada Lovelace "),
                Arrays.asList("", "", "", ""),
                Arrays.asList("grace@example.com", "not-a-number", "c-002", "Grace Hopper"),
                Arrays.asList("duplicate@example.com", "10.00", "c-001", "Duplicate Ada")
        ));
    }

    private ByteArrayInputStream secondWorkbook() throws IOException {
        return workbook(Arrays.asList(
                Arrays.asList("businessKey", "name", "email", "amount"),
                Arrays.asList("c-001", "Ada Lovelace", "ada@example.com", "1234.50"),
                Arrays.asList("c-003", "Katherine Johnson", "katherine@example.com", "100.00")
        ));
    }

    private ByteArrayInputStream updateWorkbook() throws IOException {
        return workbook(Arrays.asList(
                Arrays.asList("businessKey", "name", "email", "amount"),
                Arrays.asList("c-001", "Ada Updated", "ada.updated@example.com", "777.00")
        ));
    }

    private ByteArrayInputStream stagedInvalidEmailWorkbook() throws IOException {
        return workbook(Arrays.asList(
                Arrays.asList("businessKey", "name", "email", "amount"),
                Arrays.asList("c-010", "Valid One", "one@example.com", "10.00"),
                Arrays.asList("c-011", "Valid Two", "two@example.com", "20.00"),
                Arrays.asList("c-012", "Invalid Email", "invalid@example.com extra", "30.00")
        ));
    }

    private ByteArrayInputStream inMemoryDuplicateWorkbook() throws IOException {
        return workbook(Arrays.asList(
                Arrays.asList("businessKey", "name", "email", "amount"),
                Arrays.asList("c-020", "First", "first@example.com", "10.00"),
                Arrays.asList("c-020", "Second", "second@example.com", "20.00")
        ));
    }

    private int stagedRows(String importId) {
        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from customer_import_staging where import_id = ?",
                Integer.class,
                importId
        );
        return rows == null ? 0 : rows;
    }

    private ByteArrayInputStream workbook(List<List<String>> rows) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("customers");

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            Row row = sheet.createRow(rowIndex);
            List<String> values = rows.get(rowIndex);
            for (int cellIndex = 0; cellIndex < values.size(); cellIndex++) {
                row.createCell(cellIndex).setCellValue(values.get(cellIndex));
            }
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        return new ByteArrayInputStream(outputStream.toByteArray());
    }
}
