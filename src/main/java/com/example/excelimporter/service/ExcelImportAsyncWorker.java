package com.example.excelimporter.service;

import com.example.excelimporter.config.AsyncImportExecutorConfig;
import com.example.excelimporter.dto.ImportReport;
import com.example.excelimporter.dto.ImportStatus;
import com.example.excelimporter.dto.IssueSeverity;
import com.example.excelimporter.dto.ValidationIssue;
import com.example.excelimporter.service.imports.ImportJobStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportAsyncWorker {

    private final ExcelImportService excelImportService;
    private final ImportJobStore importJobStore;

    @Async(AsyncImportExecutorConfig.EXCEL_IMPORT_EXECUTOR)
    public CompletableFuture<Void> importAsync(String importId, String importType, Path uploadPath, String fileName) {
        try {
            excelImportService.importFile(importId, importType, uploadPath, fileName);
        } catch (RuntimeException ex) {
            saveFailedReport(importId, fileName, "ASYNC_IMPORT_FAILED",
                    ex.getMessage() == null ? "Async import failed." : ex.getMessage());
        } finally {
            deleteTempUpload(uploadPath);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void saveFailedReport(String importId, String fileName, String code, String message) {
        ImportReport report = new ImportReport(importId, fileName);
        report.setStatus(ImportStatus.FAILED);
        report.setMessage("Async import failed before completion.");
        report.addIssue(new ValidationIssue(
                IssueSeverity.BLOCKING_ERROR,
                0,
                "import",
                code,
                message
        ));
        importJobStore.saveReport(importId, report);
    }

    private void deleteTempUpload(Path uploadPath) {
        try {
            Files.deleteIfExists(uploadPath);
        } catch (IOException ex) {
            log.warn("Could not delete async import upload file: {}", uploadPath, ex);
        }
    }
}
