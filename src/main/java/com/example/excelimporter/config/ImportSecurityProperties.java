package com.example.excelimporter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
public class ImportSecurityProperties {

    private String importUsername = "import-admin";
    private String importPassword;
    private String importRole = "IMPORT_ADMIN";
}
