package com.example.excelimporter.config;

import com.example.excelimporter.service.ImportProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@RequiredArgsConstructor
public class AsyncImportExecutorConfig {

    public static final String EXCEL_IMPORT_EXECUTOR = "excelImportTaskExecutor";

    private final ImportProperties importProperties;

    @Bean(name = EXCEL_IMPORT_EXECUTOR)
    public ThreadPoolTaskExecutor excelImportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("excel-import-");
        executor.setCorePoolSize(atLeastOne(importProperties.getAsyncCorePoolSize()));
        executor.setMaxPoolSize(Math.max(
                atLeastOne(importProperties.getAsyncCorePoolSize()),
                atLeastOne(importProperties.getAsyncMaxPoolSize())
        ));
        executor.setQueueCapacity(Math.max(0, importProperties.getAsyncQueueCapacity()));
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(Math.max(0, importProperties.getAsyncAwaitTerminationSeconds()));
        return executor;
    }

    private int atLeastOne(int value) {
        return Math.max(1, value);
    }
}
