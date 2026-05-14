package com.example.excelimporter.service.imports;

import com.example.excelimporter.dto.ValidationResult;
import com.example.excelimporter.service.excel.ExcelRowMapper;
import com.example.excelimporter.service.excel.HeaderMapping;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface ExcelImportDefinition<T> {

    String getImportType();

    Collection<String> getRequiredHeaders();

    ExcelRowMapper<T> createRowMapper(HeaderMapping headerMapping);

    String businessKey(T row);

    Set<String> findExistingBusinessKeys(Collection<String> businessKeys);

    ValidationResult validate(T row, boolean existingRecord, boolean duplicateInFile);

    void bulkWrite(List<T> rows, Set<String> existingBusinessKeys, String importId);
}
