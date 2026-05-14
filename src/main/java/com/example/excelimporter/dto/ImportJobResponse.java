package com.example.excelimporter.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ImportJobResponse {

    private final String importId;
    private final String statusUrl;
}
