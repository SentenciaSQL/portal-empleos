package com.empleord.storage;

import org.springframework.core.io.Resource;

public interface StorageService {

    StoredFile store(String folder, String originalName, byte[] bytes);

    Resource load(String key);

    void delete(String key);

    record StoredFile(String key, long size) {
    }
}
