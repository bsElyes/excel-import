package com.example.excelimporter.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.import")
public class ImportProperties {

    private int chunkSize = 500;
    private int inMemoryRowThreshold = 10000;
    private int issuePreviewLimit = 500;
    private int asyncCorePoolSize = 2;
    private int asyncMaxPoolSize = 4;
    private int asyncQueueCapacity = 20;
    private int asyncAwaitTerminationSeconds = 30;
}
