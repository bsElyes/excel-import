package com.example.excelimporter.controller;

import com.example.excelimporter.dto.ImportReport;
import com.example.excelimporter.dto.ImportJobResponse;
import com.example.excelimporter.dto.ValidationIssue;
import com.example.excelimporter.service.ExcelImportException;
import com.example.excelimporter.service.ExcelImportAsyncService;
import com.example.excelimporter.service.ExcelImportService;
import com.example.excelimporter.service.ImportProperties;
import com.example.excelimporter.service.imports.ImportJobStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class CustomerImportController {

    private final ExcelImportService excelImportService;
    private final ExcelImportAsyncService excelImportAsyncService;
    private final ImportJobStore importJobStore;
    private final ImportProperties importProperties;

    @PostMapping(value = "/customers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportReport> importCustomers(@RequestParam("file") MultipartFile file) {
        return importFile("customers", file);
    }

    @PostMapping(value = "/{importType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportReport> importTypedFile(@PathVariable String importType,
                                                        @RequestParam("file") MultipartFile file) {
        return importFile(importType, file);
    }

    @PostMapping(value = "/{importType}/async", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportJobResponse> importTypedFileAsync(@PathVariable String importType,
                                                                  @RequestParam("file") MultipartFile file) {
        ImportJobResponse response = excelImportAsyncService.submit(importType, file);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/jobs/{importId}")
    public ResponseEntity<ImportReport> getImportJob(@PathVariable String importId) {
        return importJobStore.findReport(importId, importProperties.getIssuePreviewLimit())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs/{importId}/issues")
    public ResponseEntity<List<ValidationIssue>> getImportIssues(@PathVariable String importId,
                                                                 @RequestParam(defaultValue = "500") int limit,
                                                                 @RequestParam(defaultValue = "0") int offset) {
        if (!importJobStore.findReport(importId, 1).isPresent()) {
            return ResponseEntity.notFound().build();
        }
        int boundedLimit = Math.min(Math.max(1, limit), importProperties.getIssuePreviewLimit());
        return ResponseEntity.ok(importJobStore.findIssues(importId, boundedLimit, Math.max(0, offset)));
    }

    private ResponseEntity<ImportReport> importFile(String importType, MultipartFile file) {
        if (file.isEmpty()) {
            throw new ExcelImportException("Uploaded file is empty.");
        }

        try {
            ImportReport report = excelImportService.importFile(importType, file.getInputStream(), file.getOriginalFilename());
            return ResponseEntity.ok(report);
        } catch (IOException ex) {
            throw new ExcelImportException("Could not open uploaded file.", ex);
        }
    }

    @ExceptionHandler(ExcelImportException.class)
    public ResponseEntity<Map<String, String>> handleImportException(ExcelImportException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Collections.singletonMap("message", ex.getMessage()));
    }
}
