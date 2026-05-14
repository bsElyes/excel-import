package com.example.excelimporter.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;

@Getter
@RequiredArgsConstructor
public class CustomerImportRow {

    private final int rowNumber;
    private final String businessKey;
    private final String name;
    private final String email;
    private final boolean emailInvalid;
    private final BigDecimal amount;
    private final boolean amountInvalid;
}
