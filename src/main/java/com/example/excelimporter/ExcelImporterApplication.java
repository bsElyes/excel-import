package com.example.excelimporter;

import com.example.excelimporter.config.ImportSecurityProperties;
import com.example.excelimporter.service.ImportProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableConfigurationProperties({ImportProperties.class, ImportSecurityProperties.class})
@EnableAsync
public class ExcelImporterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExcelImporterApplication.class, args);
    }
}
