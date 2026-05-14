package com.example.excelimporter.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class ImportTypeLockManager {

    private final ConcurrentMap<String, ReentrantLock> locksByImportType = new ConcurrentHashMap<String, ReentrantLock>();

    public <T> T executeLocked(String importType, Supplier<T> action) {
        ReentrantLock lock = locksByImportType.computeIfAbsent(normalize(importType), key -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private String normalize(String importType) {
        return importType == null ? "" : importType.trim().toLowerCase(Locale.ENGLISH);
    }
}
