package com.example.excelimporter.service.imports;

import com.example.excelimporter.service.ExcelImportException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ExcelImportDefinitionRegistry {

    private final Map<String, ExcelImportDefinition<?>> definitionsByType = new HashMap<String, ExcelImportDefinition<?>>();

    public ExcelImportDefinitionRegistry(List<ExcelImportDefinition<?>> definitions) {
        for (ExcelImportDefinition<?> definition : definitions) {
            String importType = normalize(definition.getImportType());
            if (definitionsByType.containsKey(importType)) {
                throw new ExcelImportException("Duplicate Excel import type: " + definition.getImportType() + ".");
            }
            definitionsByType.put(importType, definition);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> ExcelImportDefinition<T> get(String importType) {
        ExcelImportDefinition<?> definition = definitionsByType.get(normalize(importType));
        if (definition == null) {
            throw new ExcelImportException("Unsupported Excel import type: " + importType + ".");
        }
        return (ExcelImportDefinition<T>) definition;
    }

    private String normalize(String importType) {
        return importType == null ? "" : importType.trim().toLowerCase(Locale.ENGLISH);
    }
}
