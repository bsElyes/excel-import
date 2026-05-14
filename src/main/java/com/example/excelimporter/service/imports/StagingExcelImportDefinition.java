package com.example.excelimporter.service.imports;

import java.util.List;

public interface StagingExcelImportDefinition<T> extends ExcelImportDefinition<T> {

    void prepareStaging(String importId);

    void stageRows(String importId, List<T> rows);

    StagedImportValidation validateStagedRows(String importId, int issuePreviewLimit);

    void mergeStagedRows(String importId);

    void clearStaging(String importId);
}
