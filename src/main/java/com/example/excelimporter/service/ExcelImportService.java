package com.example.excelimporter.service;

import com.example.excelimporter.dto.ImportReport;
import com.example.excelimporter.dto.ImportStatus;
import com.example.excelimporter.dto.IssueSeverity;
import com.example.excelimporter.dto.ValidationIssue;
import com.example.excelimporter.dto.ValidationResult;
import com.example.excelimporter.service.excel.ExcelRowHandler;
import com.example.excelimporter.service.excel.ExcelRowMapper;
import com.example.excelimporter.service.excel.HeaderMapping;
import com.example.excelimporter.service.excel.StreamingExcelRowReader;
import com.example.excelimporter.service.imports.ExcelImportDefinition;
import com.example.excelimporter.service.imports.ExcelImportDefinitionRegistry;
import com.example.excelimporter.service.imports.ImportJobStore;
import com.example.excelimporter.service.imports.StagedImportValidation;
import com.example.excelimporter.service.imports.StagingExcelImportDefinition;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final ImportProperties importProperties;
    private final StreamingExcelRowReader streamingExcelRowReader;
    private final ExcelImportDefinitionRegistry excelImportDefinitionRegistry;
    private final TransactionTemplate transactionTemplate;
    private final ImportJobStore importJobStore;
    private final ImportTypeLockManager importTypeLockManager;

    public ImportReport importCustomers(InputStream inputStream, String fileName) {
        return importFile("customers", inputStream, fileName);
    }

    public ImportReport importFile(String importType, InputStream inputStream, String fileName) {
        String importId = UUID.randomUUID().toString();
        importJobStore.createJob(importId, importType, fileName, ImportStatus.RUNNING);
        return importFile(importId, importType, inputStream, fileName);
    }

    public <T> ImportReport importFile(String importId, String importType, InputStream inputStream, String fileName) {
        return importFile(importId, importType, fileName, new ImportSourceReader() {
            @Override
            public void read(Collection<String> requiredHeaders, ExcelRowHandler rowHandler) {
                streamingExcelRowReader.read(inputStream, requiredHeaders, rowHandler);
            }
        });
    }

    public <T> ImportReport importFile(String importId, String importType, Path workbookPath, String fileName) {
        return importFile(importId, importType, fileName, new ImportSourceReader() {
            @Override
            public void read(Collection<String> requiredHeaders, ExcelRowHandler rowHandler) {
                streamingExcelRowReader.read(workbookPath, requiredHeaders, rowHandler);
            }
        });
    }

    private <T> ImportReport importFile(String importId, String importType, String fileName, ImportSourceReader sourceReader) {
        ExcelImportDefinition<T> definition = excelImportDefinitionRegistry.get(importType);
        long startedAt = System.currentTimeMillis();
        ImportReport report;
        ImportBuffer<T> buffer = new ImportBuffer<T>(importId, definition);
        boolean issuesAlreadyStored = false;
        AtomicReference<ExcelRowMapper<T>> rowMapper = new AtomicReference<ExcelRowMapper<T>>();

        try {
            importJobStore.markRunning(importId);
            sourceReader.read(definition.getRequiredHeaders(), new ExcelRowHandler() {
                @Override
                public void header(HeaderMapping headerMapping) {
                    rowMapper.set(definition.createRowMapper(headerMapping));
                }

                @Override
                public void row(int rowNumber, String[] values) {
                    ExcelRowMapper<T> mapper = rowMapper.get();
                    if (mapper == null || mapper.isBlank(values)) {
                        return;
                    }

                    buffer.add(mapper.map(rowNumber, values));
                }
            });

            report = importTypeLockManager.executeLocked(definition.getImportType(), new Supplier<ImportReport>() {
                @Override
                public ImportReport get() {
                    return buffer.isStaging()
                            ? finishStagedImport(importId, fileName, buffer.getStagingDefinition())
                            : finishInMemoryImport(importId, fileName, definition, buffer.getMemoryRows());
                }
            });
            issuesAlreadyStored = buffer.isStaging();
        } catch (Throwable ex) {
            if (isFatalJvmError(ex)) {
                throw (Error) ex;
            }
            clearStagingAfterFailure(importId, buffer);
            report = failedReport(importId, fileName, ex);
        }

        report.setDurationMillis(System.currentTimeMillis() - startedAt);
        importJobStore.saveReport(importId, report, !issuesAlreadyStored);
        if (issuesAlreadyStored && ImportStatus.FAILED.equals(report.getStatus())) {
            importJobStore.appendIssues(importId, report.getIssues());
        }
        return report;
    }

    private <T> ImportReport finishInMemoryImport(final String importId,
                                                  String fileName,
                                                  final ExcelImportDefinition<T> definition,
                                                  List<T> rows) {
        ImportReport report = new ImportReport(importId, fileName);
        report.addRows(rows.size());

        Set<String> duplicateBusinessKeys = findDuplicateBusinessKeys(rows, definition);
        Set<String> businessKeys = rows.stream()
                .map(definition::businessKey)
                .filter(this::hasText)
                .collect(Collectors.toSet());
        final Set<String> existingBusinessKeys = definition.findExistingBusinessKeys(businessKeys);
        final List<T> rowsToWrite = new ArrayList<T>();
        int potentialCreated = 0;
        int potentialUpdated = 0;
        int skipped = 0;

        for (T row : rows) {
            String businessKey = definition.businessKey(row);
            boolean duplicateInFile = hasText(businessKey) && duplicateBusinessKeys.contains(businessKey);
            boolean existingRecord = existingBusinessKeys.contains(businessKey);
            ValidationResult validationResult = definition.validate(row, existingRecord, duplicateInFile);
            report.addIssues(validationResult);

            if (validationResult.hasBlockingErrors()) {
                skipped++;
                continue;
            }

            rowsToWrite.add(row);
            if (existingRecord) {
                potentialUpdated++;
            } else {
                potentialCreated++;
            }
        }

        if (report.getBlockingErrorCount() > 0) {
            report.addSkippedRows(skipped);
            report.setStatus(ImportStatus.FAILED_VALIDATION);
            report.setMessage("Validation failed. Target tables were not modified.");
            return report;
        }

        runAtomicWrite(report, new Runnable() {
            @Override
            public void run() {
                definition.bulkWrite(rowsToWrite, existingBusinessKeys, importId);
            }
        });

        if (ImportStatus.COMPLETED.equals(report.getStatus())) {
            report.addCreatedRows(potentialCreated);
            report.addUpdatedRows(potentialUpdated);
        }
        return report;
    }

    private ImportReport finishStagedImport(final String importId,
                                            String fileName,
                                            final StagingExcelImportDefinition<?> definition) {
        ImportReport report = new ImportReport(importId, fileName);
        StagedImportValidation validation = definition.validateStagedRows(importId, importProperties.getIssuePreviewLimit());
        report.addRows(validation.getTotalRows());
        report.addSkippedRows(validation.getSkippedRows());
        report.addIssueCounts(validation.getBlockingErrorCount(), validation.getWarningCount());
        for (ValidationIssue issue : validation.getIssuePreview()) {
            report.addIssuePreview(issue);
        }

        if (validation.getBlockingErrorCount() > 0) {
            report.setStatus(ImportStatus.FAILED_VALIDATION);
            report.setMessage("Validation failed. Target tables were not modified. Full issue list is stored by import id.");
            clearStagingQuietly(importId, definition);
            return report;
        }

        runAtomicWrite(report, new Runnable() {
            @Override
            public void run() {
                definition.mergeStagedRows(importId);
                definition.clearStaging(importId);
            }
        });

        if (ImportStatus.COMPLETED.equals(report.getStatus())) {
            report.addCreatedRows(validation.getCreatedRows());
            report.addUpdatedRows(validation.getUpdatedRows());
        } else {
            clearStagingQuietly(importId, definition);
        }
        return report;
    }

    private void runAtomicWrite(ImportReport report, final Runnable writer) {
        try {
            transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                @Override
                protected void doInTransactionWithoutResult(TransactionStatus status) {
                    writer.run();
                }
            });
            report.setStatus(ImportStatus.COMPLETED);
            report.setMessage("Import completed atomically.");
        } catch (RuntimeException ex) {
            report.setStatus(ImportStatus.FAILED);
            report.setMessage("Import failed during atomic write. Target transaction was rolled back.");
            report.addIssue(new ValidationIssue(
                    IssueSeverity.BLOCKING_ERROR,
                    0,
                    "import",
                    "WRITE_FAILED",
                    ex.getMessage() == null ? "Atomic write failed." : ex.getMessage()
            ));
        }
    }

    private ImportReport failedReport(String importId, String fileName, Throwable ex) {
        ImportReport report = new ImportReport(importId, fileName);
        report.setStatus(ImportStatus.FAILED);
        report.setMessage("Import failed before target write. Target tables were not modified.");
        report.addIssue(new ValidationIssue(
                IssueSeverity.BLOCKING_ERROR,
                0,
                "import",
                "IMPORT_FAILED",
                ex.getMessage() == null ? "Import failed." : ex.getMessage()
        ));
        return report;
    }

    private <T> void clearStagingAfterFailure(String importId, ImportBuffer<T> buffer) {
        StagingExcelImportDefinition<T> activeStagingDefinition = buffer.getActiveStagingDefinition();
        if (activeStagingDefinition != null) {
            clearStagingQuietly(importId, activeStagingDefinition);
        }
    }

    private void clearStagingQuietly(String importId, StagingExcelImportDefinition<?> definition) {
        try {
            definition.clearStaging(importId);
        } catch (RuntimeException ignored) {
            // Import failure reporting should not be hidden by cleanup failure.
        }
    }

    private boolean isFatalJvmError(Throwable ex) {
        return ex instanceof ThreadDeath || ex instanceof VirtualMachineError && !(ex instanceof StackOverflowError);
    }

    private <T> Set<String> findDuplicateBusinessKeys(List<T> rows, ExcelImportDefinition<T> definition) {
        Set<String> seenBusinessKeys = new HashSet<String>();
        Set<String> duplicateBusinessKeys = new HashSet<String>();
        for (T row : rows) {
            String businessKey = definition.businessKey(row);
            if (hasText(businessKey) && !seenBusinessKeys.add(businessKey)) {
                duplicateBusinessKeys.add(businessKey);
            }
        }
        return duplicateBusinessKeys;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private final class ImportBuffer<T> {

        private final String importId;
        private final ExcelImportDefinition<T> definition;
        private final List<T> memoryRows = new ArrayList<T>();
        private final List<T> stagingChunk = new ArrayList<T>(importProperties.getChunkSize());
        private StagingExcelImportDefinition<T> stagingDefinition;

        private ImportBuffer(String importId, ExcelImportDefinition<T> definition) {
            this.importId = importId;
            this.definition = definition;
        }

        private void add(T row) {
            if (isStaging()) {
                stage(row);
                return;
            }

            memoryRows.add(row);
            if (memoryRows.size() > importProperties.getInMemoryRowThreshold()) {
                switchToStaging();
            }
        }

        private boolean isStaging() {
            return stagingDefinition != null;
        }

        private List<T> getMemoryRows() {
            return memoryRows;
        }

        private StagingExcelImportDefinition<T> getStagingDefinition() {
            flushStagingChunk();
            return stagingDefinition;
        }

        private StagingExcelImportDefinition<T> getActiveStagingDefinition() {
            return stagingDefinition;
        }

        @SuppressWarnings("unchecked")
        private void switchToStaging() {
            if (!(definition instanceof StagingExcelImportDefinition)) {
                throw new ExcelImportException("Import type does not support staging for large files.");
            }

            stagingDefinition = (StagingExcelImportDefinition<T>) definition;
            stagingDefinition.prepareStaging(importId);
            stagingDefinition.stageRows(importId, memoryRows);
            memoryRows.clear();
        }

        private void stage(T row) {
            stagingChunk.add(row);
            if (stagingChunk.size() >= importProperties.getChunkSize()) {
                flushStagingChunk();
            }
        }

        private void flushStagingChunk() {
            if (stagingDefinition != null && !stagingChunk.isEmpty()) {
                stagingDefinition.stageRows(importId, stagingChunk);
                stagingChunk.clear();
            }
        }
    }

    private interface ImportSourceReader {

        void read(Collection<String> requiredHeaders, ExcelRowHandler rowHandler);
    }
}
