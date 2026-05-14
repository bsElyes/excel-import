package com.example.excelimporter.service.excel;

import com.example.excelimporter.service.ExcelImportException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HeaderMapping {

    private final Map<String, Integer> indexesByHeader;
    private final int maxColumnIndex;

    private HeaderMapping(Map<String, Integer> indexesByHeader, int maxColumnIndex) {
        this.indexesByHeader = indexesByHeader;
        this.maxColumnIndex = maxColumnIndex;
    }

    static HeaderMapping fromHeader(String[] headerValues, Collection<String> requiredHeaders) {
        Map<String, Integer> indexesByHeader = new HashMap<String, Integer>();
        int maxColumnIndex = -1;

        for (int index = 0; index < headerValues.length; index++) {
            String normalizedHeader = normalize(headerValues[index]);
            if (!normalizedHeader.isEmpty()) {
                indexesByHeader.put(normalizedHeader, index);
            }
        }

        for (String requiredHeader : requiredHeaders) {
            Integer columnIndex = indexesByHeader.get(normalize(requiredHeader));
            if (columnIndex == null) {
                throw new ExcelImportException("Missing required Excel column: " + requiredHeader + ".");
            }
            maxColumnIndex = Math.max(maxColumnIndex, columnIndex);
        }

        return new HeaderMapping(indexesByHeader, maxColumnIndex);
    }

    public int requiredIndex(String headerName) {
        Integer columnIndex = indexesByHeader.get(normalize(headerName));
        if (columnIndex == null) {
            throw new ExcelImportException("Missing required Excel column: " + headerName + ".");
        }
        return columnIndex;
    }

    int getMaxColumnIndex() {
        return maxColumnIndex;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ENGLISH);
    }
}
