package com.example.excelimporter.service;

import com.example.excelimporter.dto.ImportJobResponse;
import com.example.excelimporter.dto.ImportReport;
import com.example.excelimporter.dto.ImportStatus;
import com.example.excelimporter.dto.IssueSeverity;
import com.example.excelimporter.dto.ValidationIssue;
import com.example.excelimporter.service.imports.ImportJobStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportAsyncService {

    private final ImportJobStore importJobStore;
    private final ExcelImportAsyncWorker excelImportAsyncWorker;

    public ImportJobResponse submit(String importType, MultipartFile file) {
        if (file.isEmpty()) {
            throw new ExcelImportException("Uploaded file is empty.");
        }

        String importId = UUID.randomUUID().toString();
        Path uploadPath = copyUpload(file, importId);
        importJobStore.createJob(importId, importType, file.getOriginalFilename(), ImportStatus.PENDING);
        try {
            excelImportAsyncWorker.importAsync(importId, importType, uploadPath, file.getOriginalFilename());
        } catch (RuntimeException ex) {
            deleteTempUpload(uploadPath);
            saveRejectedJob(importId, file.getOriginalFilename(), ex);
            throw new ExcelImportException("Import queue is full or unavailable. Try again later.", ex);
        }
        return new ImportJobResponse(importId, "/api/imports/jobs/" + importId);
    }

    private Path copyUpload(MultipartFile file, String importId) {
        try {
            Path uploadPath = Files.createTempFile("excel-import-" + importId + "-", ".xlsx");
            Files.copy(file.getInputStream(), uploadPath, StandardCopyOption.REPLACE_EXISTING);
            return uploadPath;
        } catch (IOException ex) {
            throw new ExcelImportException("Could not store uploaded file for async import.", ex);
        }
    }

    private void saveRejectedJob(String importId, String fileName, RuntimeException ex) {
        ImportReport report = new ImportReport(importId, fileName);
        report.setStatus(ImportStatus.FAILED);
        report.setMessage("Async import was rejected before processing.");
        report.addIssue(new ValidationIssue(
                IssueSeverity.BLOCKING_ERROR,
                0,
                "import",
                "ASYNC_QUEUE_REJECTED",
                ex.getMessage() == null ? "Import queue is full or unavailable." : ex.getMessage()
        ));
        importJobStore.saveReport(importId, report);
    }

    private void deleteTempUpload(Path uploadPath) {
        try {
            Files.deleteIfExists(uploadPath);
        } catch (IOException ex) {
            log.warn("Could not delete rejected async import upload file: {}", uploadPath, ex);
        }
    }
}
