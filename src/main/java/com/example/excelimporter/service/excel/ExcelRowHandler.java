package com.example.excelimporter.service.excel;

public interface ExcelRowHandler {

    void header(HeaderMapping headerMapping);

    void row(int rowNumber, String[] values);
}
