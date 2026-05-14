package com.example.excelimporter.service.excel;

public interface ExcelRowMapper<T> {

    boolean isBlank(String[] values);

    T map(int rowNumber, String[] values);
}
