package com.example.excelimporter.service.excel;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;

public interface StreamingExcelRowReader {

    void read(InputStream inputStream, Collection<String> requiredHeaders, ExcelRowHandler rowHandler);

    void read(Path workbookPath, Collection<String> requiredHeaders, ExcelRowHandler rowHandler);
}
